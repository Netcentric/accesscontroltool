package biz.netcentric.cq.tools.actool.yaml;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.Queue;

import org.snakeyaml.engine.v2.api.DumpSettings;
import org.snakeyaml.engine.v2.api.DumpSettingsBuilder;
import org.snakeyaml.engine.v2.api.LoadSettings;
import org.snakeyaml.engine.v2.api.LoadSettingsBuilder;
import org.snakeyaml.engine.v2.api.YamlOutputStreamWriter;
import org.snakeyaml.engine.v2.api.lowlevel.Parse;
import org.snakeyaml.engine.v2.composer.Composer;
import org.snakeyaml.engine.v2.constructor.BaseConstructor;
import org.snakeyaml.engine.v2.constructor.StandardConstructor;
import org.snakeyaml.engine.v2.emitter.Emitter;
import org.snakeyaml.engine.v2.events.Event;
import org.snakeyaml.engine.v2.events.Event.ID;
import org.snakeyaml.engine.v2.exceptions.EmitterException;
import org.snakeyaml.engine.v2.exceptions.ParserException;
import org.snakeyaml.engine.v2.nodes.Node;
import org.snakeyaml.engine.v2.parser.Parser;

import biz.netcentric.cq.tools.actool.configmodel.AcConfiguration;

/**
 * Transforms the pre 4.0 YAML configuration file format to the new 4.0 format.
 * TODO: rename to something like parser
 */
public class YamlTransformer {

    private final LoadSettingsBuilder loadSettingsBuilder;
    private final DumpSettingsBuilder dumpSettingsBuilder;
    
    // state machine
    // somehow make it mutable
    //private final BaseConstructor constructor;
    private Collection<YamlEventTransformer> transformers;
    
    public YamlTransformer(Collection<YamlEventTransformer> transformers) {
        this.transformers = transformers;
        this.loadSettingsBuilder = LoadSettings.builder();
        this.dumpSettingsBuilder = DumpSettings.builder();
        // add scalar resolver for EL expressions
        
        // add scalar resolver for interpolations
       // this.constructor = new StandardConstructor(loadSettings);
    }

    private void transformEvent(Event event, List<Event> transformedEvents) {
        transformers.stream().map(transformer -> transformer.transform(event, transformedEvents)).forEach(transformedEvents::addAll);
    }

    protected List<Event> getTransformedEvents(InputStream inputStream, String label) {
        Parse parse = new Parse(loadSettingsBuilder.setLabel(label).build());
        Iterable<Event> events = parse.parseInputStream(inputStream);
        
        List<Event> originalEvents = new ArrayList<>();
        List<Event> transformedEvents = new ArrayList<>();
        events.forEach(event -> {
            originalEvents.add(event);
            transformEvent(event, transformedEvents);
        });
        return transformedEvents;
    }

    public void transform(InputStream inputStream, String label, OutputStream outputStream) throws IOException {
        transform(inputStream, label, outputStream, StandardCharsets.UTF_8);
    }

    /**
     * Transforms the input stream and writes the result to the output stream. The transformation is performed by all of the bound
     * {@link YamlEventTransformer}s.
     * @param inputStream
     * @param outputStream
     * @param charset
     * @throws IOException
     */
    public void transform(InputStream inputStream, String label, OutputStream outputStream, Charset charset) throws IOException {
        List<Event> events = getTransformedEvents(inputStream, label);
        try {
            final Emitter emitter = new Emitter(dumpSettingsBuilder.build(), new YamlOutputStreamWriter(outputStream, charset) {
                @Override
                public void processIOException(IOException e) {
                    throw new UncheckedIOException(e);
                }
            });
            int index = 0;
            for (Event event : events) {
                try {
                    emitter.emit(event);
                } catch (EmitterException e) {
                    throw new ParserException("Error emitting event " + index , event.getStartMark(), e.getMessage(), event.getStartMark());
                }
                index++;
            }
        } catch (UncheckedIOException e) {
            throw e.getCause();
        }
    }

    public AcConfiguration parse(InputStream inputStream, String label) {
        LoadSettings loadSettings = loadSettingsBuilder.setLabel(label).build();
        List<Event> events = getTransformedEvents(inputStream, label);
        Composer composer = new Composer(loadSettings, new EventParser(events.iterator()));
        Optional<Node> nodeOptional = composer.getSingleNode();
        
        return null;//(AcConfiguration)constructor.constructSingleDocument(nodeOptional);
    }

    // TODO: rely on internal snakeyaml classes once https://bitbucket.org/snakeyaml/snakeyaml-engine/issues/59/provide-eventparser-implementation is done
    private static final class EventParser implements Parser {
        private Iterator<Event> events;
        private Event currentEvent;

        public EventParser(Iterator<Event> events) {
            this.events = events;
        }

        @Override
        public boolean hasNext() {
            return events.hasNext();
        }

        @Override
        public boolean checkEvent(ID choice) {
           // this violates the contract of the method (https://bitbucket.org/snakeyaml/snakeyaml-engine/issues/57/improve-javadoc-of-parser)
           return peekEvent().getEventId().equals(choice);
        }

        @Override
        public Event peekEvent() {
            if (currentEvent == null) {
                currentEvent = events.next();
            }
            return currentEvent;
        }

        @Override
        public Event next() {
            Event event = peekEvent();
            currentEvent = null;
            return event;
        }
    }
}
