package biz.netcentric.cq.tools.actool.yaml;

import java.util.Collection;
import java.util.List;

import org.snakeyaml.engine.v2.events.CollectionStartEvent;
import org.snakeyaml.engine.v2.events.Event;

public class AddTagsYamlEventTransformer extends YamlCollectionAwareEventTransformer {

    @Override
    public Collection<Event> transform(Event input, List<Event> processedEvents) {
        if (input instanceof CollectionStartEvent) {
            CollectionStartEvent collectionStartEvent = (CollectionStartEvent) input;
            // check which is the closest parent collection value
            
        }
        return processedEvents;
    }

}
