package org.matsim.prepare;

import org.apache.log4j.Logger;
import org.geotools.feature.simple.SimpleFeatureBuilder;
import org.geotools.feature.simple.SimpleFeatureTypeBuilder;
import org.locationtech.jts.geom.Point;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.network.Node;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.network.io.MatsimNetworkReader;
import org.matsim.core.scenario.MutableScenario;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.core.utils.geometry.geotools.MGC;
import org.matsim.core.utils.gis.ShapeFileWriter;
import org.matsim.utils.gis.matsim2esri.network.Nodes2ESRIShape;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.referencing.crs.CoordinateReferenceSystem;

import java.util.ArrayList;
import java.util.Collection;

public class NetworkNodesToShape {
    private static final Logger log = Logger.getLogger(NetworkNodesToShape.class);
    private final Network network;
    private final String filename;
    private SimpleFeatureBuilder builder;

    public NetworkNodesToShape(Network network, String filename, String coordinateSystem) {
        this(network, filename, MGC.getCRS(coordinateSystem));
    }

    public NetworkNodesToShape(Network network, String filename, CoordinateReferenceSystem crs) {
        this.network = network;
        this.filename = filename;
        this.initFeatureType(crs);
    }

    public void write() {
        Collection<SimpleFeature> features = new ArrayList();
        Node[] var2 = NetworkUtils.getSortedNodes(this.network);
        int var3 = var2.length;

        for(int var4 = 0; var4 < var3; ++var4) {
            Node node = var2[var4];
            features.add(this.getFeature(node));
        }

        ShapeFileWriter.writeGeometries(features, this.filename);
    }

    private SimpleFeature getFeature(Node node) {
        Point p = MGC.coord2Point(node.getCoord());

        try {
            return this.builder.buildFeature((String)null, new Object[]{p, node.getId().toString()});
        } catch (IllegalArgumentException var4) {
            throw new RuntimeException(var4);
        }
    }

    private void initFeatureType(CoordinateReferenceSystem crs) {
        SimpleFeatureTypeBuilder typeBuilder = new SimpleFeatureTypeBuilder();
        typeBuilder.setName("node");
        typeBuilder.setCRS(crs);
        typeBuilder.add("location", Point.class);
        typeBuilder.add("ID", String.class);
        this.builder = new SimpleFeatureBuilder(typeBuilder.buildFeatureType());
    }

    public static void main(String[] args) {
        String netfile = null;
        String outputFile = null;
        if (args.length == 0) {
            netfile = "inputfile.xml";
            outputFile = "output.shp";
        } else if (args.length == 2) {
            netfile = args[0];
            outputFile = args[1];
        } else {
            log.error("Arguments cannot be interpreted.  Aborting ...");
            System.exit(-1);
        }

        MutableScenario scenario = (MutableScenario) ScenarioUtils.createScenario(ConfigUtils.createConfig());
        log.info("loading network from " + netfile);
        Network network = scenario.getNetwork();
        (new MatsimNetworkReader(scenario.getNetwork())).readFile(netfile);
        log.info("done.");
        (new Nodes2ESRIShape(network, outputFile, "DHDN_GK4")).write();
    }
}
