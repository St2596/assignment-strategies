package org.matsim.outputAnalysis;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.matsim.core.api.experimental.events.EventsManager;
import org.matsim.core.events.EventsUtils;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Map;

public class RunEventHandlers {

    private static final String eventsFile = "input/output_events.xml";
    private static final String outFile = "output.csv";

    public static void main(String[] args) {

        EventsManager manager = EventsUtils.createEventsManager();
        LinkVolumeEventHandler linkHandler = new LinkVolumeEventHandler();
        manager.addHandler(linkHandler);

        EventsUtils.readEvents(manager, eventsFile);

        Map<String, Integer> volumes = linkHandler.getVolumes();

        try (BufferedWriter writer = Files.newBufferedWriter(Paths.get(outFile)); CSVPrinter printer = CSVFormat.DEFAULT.withDelimiter(';').withHeader("Hour", "Value").print(writer)) {
            for (Map.Entry<String, Integer> volume : volumes.entrySet()) {
                printer.printRecord(volume.getKey(), volume.getValue());
                printer.println();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}