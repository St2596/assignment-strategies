package org.matsim.outputAnalysis;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.events.ActivityEndEvent;
import org.matsim.api.core.v01.events.PersonDepartureEvent;
import org.matsim.api.core.v01.events.TransitDriverStartsEvent;
import org.matsim.api.core.v01.events.handler.ActivityEndEventHandler;
import org.matsim.api.core.v01.events.handler.PersonDepartureEventHandler;
import org.matsim.api.core.v01.events.handler.TransitDriverStartsEventHandler;
import org.matsim.api.core.v01.population.Person;

import java.util.*;

public class MainModeHandler implements TransitDriverStartsEventHandler, PersonDepartureEventHandler, ActivityEndEventHandler {

    private static final List<String> modes = List.of(TransportMode.drt, TransportMode.walk, TransportMode.bike, TransportMode.ride, TransportMode.car, TransportMode.pt, TransportMode.airplane);
    private final Set<Id<Person>> transitDrivers = new HashSet<>();
    private final Map<Id<Person>, List<String>> personTrips = new HashMap<>();

    public Map<Id<Person>, List<String>> getPersonTrips() {
        return personTrips;
    }

    @Override
    public void handleEvent(ActivityEndEvent e) {

        if (transitDrivers.contains(e.getPersonId()) || e.getActType().endsWith(" interaction")) return;

        personTrips.computeIfAbsent(e.getPersonId(), id -> new ArrayList<>()).add("");
    }

    @Override
    public void handleEvent(PersonDepartureEvent e) {

        if (transitDrivers.contains(e.getPersonId())) return;
        List<String> trips = personTrips.getOrDefault(e.getPersonId(), new ArrayList<>());
        updateLastTripMode(trips, e.getLegMode());
    } 

    @Override
    public void handleEvent(TransitDriverStartsEvent transitDriverStartsEvent) {
        transitDrivers.add(transitDriverStartsEvent.getDriverId());
    }

    private void updateLastTripMode(List<String> trips, String newMode) {
        if (!trips.isEmpty()) {
            String lastMode = trips.get(trips.size() - 1);
            trips.set(trips.size() - 1, getMainMode(lastMode, newMode));
        }
    }
    private String getMainMode(String current, String newMode) {
        Integer currentIndex = modes.indexOf(current);
        Integer newIndex = modes.indexOf(newMode);
        return currentIndex > newIndex ? current : newMode;
    }

}
