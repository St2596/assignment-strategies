package org.matsim.drtMainModeIdentifier;

import com.google.inject.Inject;
import org.apache.log4j.Logger;
import org.matsim.analysis.TransportPlanningMainModeIdentifier;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.population.Leg;
import org.matsim.api.core.v01.population.PlanElement;
import org.matsim.core.router.AnalysisMainModeIdentifier;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class MelbourneIntermodalPtDrtRouterAnalysisModeIdentifier implements AnalysisMainModeIdentifier {
    private final List<String> modeHierarchy = new ArrayList<>() ;
    private final List<String> drtModes;
    private static final Logger log = Logger.getLogger(MelbourneIntermodalPtDrtRouterAnalysisModeIdentifier.class);
    public static final String ANALYSIS_MAIN_MODE_PT_WITH_DRT_USED_FOR_ACCESS_OR_EGRESS = "pt_w_drt_used";

    @Inject
    public MelbourneIntermodalPtDrtRouterAnalysisModeIdentifier() {
        drtModes = Arrays.asList(TransportMode.drt);

        modeHierarchy.add( TransportMode.walk ) ;
        modeHierarchy.add( "bicycle" );
        modeHierarchy.add( "bike" );
        modeHierarchy.add( TransportMode.ride ) ;
        modeHierarchy.add( TransportMode.car ) ;
        for (String drtMode: drtModes) {
            modeHierarchy.add( drtMode ) ;
        }
        modeHierarchy.add( TransportMode.pt ) ;

    }

    @Override public String identifyMainMode( List<? extends PlanElement> planElements ) {
        int mainModeIndex = -1 ;
        List<String> modesFound = new ArrayList<>();
        for ( PlanElement pe : planElements ) {
            int index;
            String mode;
            if ( pe instanceof Leg) {
                Leg leg = (Leg) pe ;
                mode = leg.getMode();
            } else {
                continue;
            }
            if (mode.equals(TransportMode.non_network_walk)) {
                continue;
            }
            if (mode.equals(TransportMode.transit_walk)) {
                mode = TransportMode.walk;
            } else {
                for (String drtMode: drtModes) {
                    if (mode.equals(drtMode + "_fallback")) {// transit_walk / drt_walk / ... to be replaced by _fallback soon
                        mode = TransportMode.walk;
                    }
                }
            }
            modesFound.add(mode);
            index = modeHierarchy.indexOf( mode ) ;
            if ( index < 0 ) {
                throw new RuntimeException("unknown mode=" + mode ) ;
            }
            if ( index > mainModeIndex ) {
                mainModeIndex = index ;
            }
        }
        if (mainModeIndex == -1) {
            throw new RuntimeException("no main mode found for trip " + planElements.toString() ) ;
        }

        String mainMode = modeHierarchy.get( mainModeIndex ) ;
        // differentiate pt monomodal/intermodal
        if (mainMode.equals(TransportMode.pt)) {
            boolean isDrtPt = false;
            for (String modeFound: modesFound) {
                if (modeFound.equals(TransportMode.pt)) {
                    continue;
                } else if (modeFound.equals(TransportMode.walk)) {
                    continue;
                } else if (drtModes.contains(modeFound)) {
                    isDrtPt = true;
                } else {
                    log.error("unknown intermodal pt trip: " + planElements.toString());
                    throw new RuntimeException("unknown intermodal pt trip");
                }
            }

            if (isDrtPt) {
                return MelbourneIntermodalPtDrtRouterAnalysisModeIdentifier.ANALYSIS_MAIN_MODE_PT_WITH_DRT_USED_FOR_ACCESS_OR_EGRESS;
            } else {
                return TransportMode.pt;
            }

        } else {
            return mainMode;
        }
    }
}