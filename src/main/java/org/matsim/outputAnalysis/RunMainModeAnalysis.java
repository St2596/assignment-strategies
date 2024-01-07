package org.matsim.outputAnalysis;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.population.Person;
import org.matsim.core.api.experimental.events.EventsManager;
import org.matsim.core.events.EventsUtils;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class RunMainModeAnalysis {

    public static void main(String[] args) {

        EventsManager manager = EventsUtils.createEventsManager();
        MainModeHandler handler = new MainModeHandler();
        manager.addHandler(handler);
        EventsUtils.readEvents(manager, "input/output_events.xml.gz");

        Map<Id<Person>, List<String>> personTrips = handler.getPersonTrips();
        Map<String, Integer> modeCounts = personTrips.values().stream()
                .flatMap(Collection::stream)
                .collect(Collectors.toMap(mode -> mode, mode -> 1, Integer::sum));

        double totalTrips = modeCounts.values().stream()
                .mapToDouble(d -> d)
                .sum();;

        try (BufferedWriter writer = Files.newBufferedWriter(Paths.get("output/modes.csv")); CSVPrinter printer = CSVFormat.DEFAULT.withDelimiter(',').withHeader("Mode", "Count", "Share").print(writer)) {
            for (Map.Entry<String, Integer> entry : modeCounts.entrySet()) {
                printer.printRecord(entry.getKey(), entry.getValue(), entry.getValue() / totalTrips);
            }
            printer.printRecord("total", totalTrips, 1.0);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}