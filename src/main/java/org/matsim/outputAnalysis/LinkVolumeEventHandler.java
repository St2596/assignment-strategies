package org.matsim.outputAnalysis;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.events.LinkLeaveEvent;
import org.matsim.api.core.v01.events.handler.LinkLeaveEventHandler;
import org.matsim.api.core.v01.network.Link;

import java.util.HashMap;
import java.util.Map;

public class LinkVolumeEventHandler implements LinkLeaveEventHandler {
    private static final Id<Link> linkOfInterest = Id.createLinkId("LinkID");
    private final Map<String, Integer> volumes = new HashMap<>();

    Map<String, Integer> getVolumes() {
        return volumes;
    }

    @Override
    public void handleEvent(LinkLeaveEvent event) {

        if (event.getLinkId().equals(linkOfInterest)) {
            String key = getKey(event.getTime());
            volumes.merge(key, 1, Integer::sum);
        }
    }
    private String getKey(double time) {
        return Integer.toString((int) (time / 3600));
    }

}
