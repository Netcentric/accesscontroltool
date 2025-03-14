package biz.netcentric.cq.tools.actool.yaml;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.ListIterator;
import java.util.Queue;

import org.snakeyaml.engine.v2.events.Event;

public class YamlCollectionAwareEventTransformer implements YamlEventTransformer {

    Queue<ProcessingInstruction> sequences = Collections.asLifoQueue(new ArrayDeque<>());
    Queue<ProcessingInstruction> mappings = Collections.asLifoQueue(new ArrayDeque<>());

    private static final class ProcessingInstruction {
        boolean isSkipped = false;
        
        public void skip() {
            isSkipped = true;
        }
        
        public boolean isSkipped() {
            return isSkipped;
        }
    }
    
    protected void hideSurroundingSequence(List<Event> processedEvents) {
        ProcessingInstruction surroundingSequence = sequences.peek();
        if (surroundingSequence == null) {
            // bail out if already skipped or no sequence around it
            return;
        }
        if (surroundingSequence.isSkipped()) {
            // take care of nested maps, those need to be dissolved so that only one map is kept
            ListIterator<Event> iter = processedEvents.listIterator(processedEvents.size());
            if (iter.hasPrevious()) {
                Event subsequenEvent = iter.previous();
                if (subsequenEvent.getEventId().equals(Event.ID.MappingStart)) {
                    iter.remove();
                    if (iter.hasPrevious()) {
                        subsequenEvent = iter.previous();
                        if (subsequenEvent.getEventId().equals(Event.ID.MappingEnd)) {
                            iter.remove();
                        }
                    } else {
                        throw new IllegalStateException("No map end found");
                    }
                }
            }
        } else {
            surroundingSequence.skip();
            // just remove closest sequence start (from the end)
            // also remove a map start event if it comes right after the sequence start
            ListIterator<Event> iter = processedEvents.listIterator(processedEvents.size());
            while(iter.hasPrevious()) {
                Event event = iter.previous();
                if (event.getEventId().equals(Event.ID.SequenceStart)) {
                    iter.remove();
                    return;
                }
            }
            throw new IllegalStateException("No sequence start found");
        }
    }
    
    @Override
    public Collection<Event> transform(Event input, List<Event> processedEvents) {
        if (input.getEventId().equals(Event.ID.SequenceStart)) {
            sequences.add(new ProcessingInstruction());
        } else if (input.getEventId().equals(Event.ID.SequenceEnd)) {
            if (sequences.remove().isSkipped()) {
                return Collections.emptyList();
            }
        } else if (input.getEventId().equals(Event.ID.MappingStart)) {
            mappings.add(new ProcessingInstruction());
        } else if (input.getEventId().equals(Event.ID.MappingEnd)) {
            if (mappings.remove().isSkipped()) {
                return Collections.emptyList();
            }
        }
        return Collections.singletonList(input);
    }

}
