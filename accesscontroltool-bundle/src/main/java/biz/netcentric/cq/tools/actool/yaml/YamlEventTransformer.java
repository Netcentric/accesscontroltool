package biz.netcentric.cq.tools.actool.yaml;

import java.io.UncheckedIOException;
import java.util.Collection;
import java.util.List;

import org.snakeyaml.engine.v2.events.Event;

/**
 * Allows to modify the event stream of a YAML file.
 */
public interface YamlEventTransformer {
    
    /**
     * Transforms the input event into a collection of events. Called for each event in the input YAML file.
     * @param input the event to transform
     * @param processedEvents all previously processed events, may be modified as well within this method
     * @return the transformed events (which are appended to the {@code processedEvents} list by the caller).
     * @throws UncheckedIOException for any IO related errors
     */
    Collection<Event> transform(Event input, List<Event> processedEvents);
}
