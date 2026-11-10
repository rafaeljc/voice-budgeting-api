package dio.budgeting.infrastructure.ai;

import dio.budgeting.domain.UserId;
import dio.budgeting.infrastructure.ai.event.AiUsageMeasuredEvent;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationHandler;
import io.micrometer.tracing.Tracer;
import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.audio.transcription.observation.TranscriptionModelObservationContext;
import org.springframework.ai.audio.tts.observation.TextToSpeechModelObservationContext;
import org.springframework.ai.chat.observation.ChatModelObservationContext;
import org.springframework.ai.model.observation.ModelObservationContext;
import org.springframework.ai.observation.conventions.AiObservationAttributes;
import org.springframework.ai.openai.metadata.OpenAiAudioTranscriptionResponseMetadata;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

// Measures from the Spring AI observation, not from the gateways: the chat response enters the context before the
// tools run, so a call whose tool throws is still measured. Runs on the request thread, where the user and the trace
// are known
@Component
public class AiUsageObservationHandler implements ObservationHandler<ModelObservationContext<?, ?>> {
    private static final Logger log = LoggerFactory.getLogger(AiUsageObservationHandler.class);

    private final ApplicationEventPublisher eventPublisher;
    private final Tracer tracer;
    private final Clock clock;

    public AiUsageObservationHandler(ApplicationEventPublisher eventPublisher, Tracer tracer, Clock clock) {
        this.eventPublisher = eventPublisher;
        this.tracer = tracer;
        this.clock = clock;
    }

    @Override
    public boolean supportsContext(Observation.Context context) {
        return context instanceof TranscriptionModelObservationContext
                || context instanceof ChatModelObservationContext
                || context instanceof TextToSpeechModelObservationContext;
    }

    @Override
    public void onStop(ModelObservationContext<?, ?> context) {
        try {
            var usage = usage(context);
            var userId = userId();
            if (usage != null && userId != null) {
                eventPublisher.publishEvent(
                        new AiUsageMeasuredEvent(userId, traceId(), clock.instant(), model(context), usage));
            }
        } catch (RuntimeException e) {
            // Only types: the call to the model is never affected
            log.atWarn()
                    .addKeyValue("stage", context.getClass().getSimpleName())
                    .addKeyValue("errorType", e.getClass().getSimpleName())
                    .log("AI usage not measured");
        }
    }

    private static AiUsageMeasuredEvent.Usage usage(ModelObservationContext<?, ?> context) {
        if (context.getError() != null) {
            return null;
        }
        return switch (context) {
            case TranscriptionModelObservationContext transcription -> transcriptionUsage(transcription);
            case ChatModelObservationContext chat -> chatUsage(chat);
            // The Spring AI keeps no response of the synthesis in the context: an observation without error is a
            // response
            case TextToSpeechModelObservationContext speech ->
                new AiUsageMeasuredEvent.Usage.Speech(
                        speech.getRequest().getInstructions().getText().length());
            default -> null;
        };
    }

    private static AiUsageMeasuredEvent.Usage transcriptionUsage(TranscriptionModelObservationContext context) {
        var response = context.getResponse();
        if (response == null
                || !(response.getMetadata() instanceof OpenAiAudioTranscriptionResponseMetadata metadata)
                || metadata.getDuration() == null) {
            return null;
        }
        return new AiUsageMeasuredEvent.Usage.Transcription(metadata.getDuration());
    }

    private static AiUsageMeasuredEvent.Usage chatUsage(ChatModelObservationContext context) {
        var response = context.getResponse();
        if (response == null) {
            return null;
        }
        var usage = response.getMetadata().getUsage();
        var cached = usage.getCacheReadInputTokens();
        return new AiUsageMeasuredEvent.Usage.Assistant(
                orZero(usage.getPromptTokens()), cached == null ? 0 : cached, orZero(usage.getCompletionTokens()));
    }

    private static long orZero(Integer tokens) {
        return tokens == null ? 0 : tokens;
    }

    private static String model(ModelObservationContext<?, ?> context) {
        var response = context.getLowCardinalityKeyValue(AiObservationAttributes.RESPONSE_MODEL.value());
        var model = response != null
                ? response
                : context.getLowCardinalityKeyValue(AiObservationAttributes.REQUEST_MODEL.value());
        return model == null ? null : model.getValue();
    }

    private static UserId userId() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof Jwt jwt) {
            return UserId.parse(jwt.getSubject()).orElse(null);
        }
        return null;
    }

    private String traceId() {
        var span = tracer.currentSpan();
        return span == null ? null : span.context().traceId();
    }
}
