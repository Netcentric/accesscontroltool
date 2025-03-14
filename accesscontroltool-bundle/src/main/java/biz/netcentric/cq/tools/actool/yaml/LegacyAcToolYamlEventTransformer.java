package biz.netcentric.cq.tools.actool.yaml;

import java.util.Collection;
import java.util.List;

import org.snakeyaml.engine.v2.events.Event;
import org.snakeyaml.engine.v2.events.ScalarEvent;

/** Expects that EL expressions are resolved in the input yaml file. */
public class LegacyAcToolYamlEventTransformer extends YamlCollectionAwareEventTransformer {

    @Override
    public Collection<Event> transform(Event input, List<Event> processedEvents) {
        if (input instanceof ScalarEvent) {
            ScalarEvent scalarEvent = (ScalarEvent) input;
            switch (scalarEvent.getValue()) {
                case "group_config":
                case "user_config":
                case "ace_config":
                    // strip sequence start event before and correlating sequence end event after
                    hideSurroundingSequence(processedEvents);
            }
        }
        return super.transform(input, processedEvents);
    }

}
