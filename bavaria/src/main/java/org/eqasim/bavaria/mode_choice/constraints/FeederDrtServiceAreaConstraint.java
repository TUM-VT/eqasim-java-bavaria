package org.eqasim.bavaria.mode_choice.constraints;

import java.util.Collection;
import java.util.List;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.prep.PreparedGeometry;
import org.locationtech.jts.geom.prep.PreparedGeometryFactory;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.population.Activity;
import org.matsim.api.core.v01.population.Leg;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.PlanElement;
import org.matsim.contribs.discrete_mode_choice.model.DiscreteModeChoiceTrip;
import org.matsim.contribs.discrete_mode_choice.model.trip_based.TripConstraint;
import org.matsim.contribs.discrete_mode_choice.model.trip_based.TripConstraintFactory;
import org.matsim.contribs.discrete_mode_choice.model.trip_based.candidates.RoutedTripCandidate;
import org.matsim.contribs.discrete_mode_choice.model.trip_based.candidates.TripCandidate;
import org.matsim.core.utils.gis.GeoFileReader;
import org.geotools.api.feature.simple.SimpleFeature;

import com.google.inject.Inject;
import com.google.inject.name.Named;

/**
 * Checks whether the DRT sub-legs of a feeder_drt trip are within the
 * configured service area polygon.
 *
 * The eqasim ShapeFile TripConstraint checks origin/destination of the
 * *overall* trip, which for feeder_drt may lie outside the DRT service area
 * by design. This constraint instead inspects the individual DRT legs inside
 * the intermodal trip and enforces that their pickup and dropoff coordinates
 * lie within the polygon.
 *
 * Binding example in your EqasimConfigurator subclass:
 *
 *   addTripConstraintFactory(
 *       FeederDrtServiceAreaConstraint.NAME,
 *       FeederDrtServiceAreaConstraint.Factory.class);
 *
 * Config entry in DiscreteModeChoice:
 *   <param name="tripConstraints" value="..., FeederDrtServiceAreaConstraint"/>
 */
public class FeederDrtServiceAreaConstraint implements TripConstraint {

    public static final String NAME = "FeederDrtServiceAreaConstraint";

    /** Routing mode of the access/egress DRT mode (must match your config). */
    private static final String DRT_MODE = "drt";

    /** Routing mode of the feeder trip as seen by DMC. */
    private static final String FEEDER_MODE = "feeder_drt";

    private final Network network;
    private final PreparedGeometry serviceArea;
    private final GeometryFactory geometryFactory = new GeometryFactory();

    public FeederDrtServiceAreaConstraint(Network network, PreparedGeometry serviceArea) {
        this.network = network;
        this.serviceArea = serviceArea;
    }

    @Override
    public boolean validateBeforeEstimation(
            DiscreteModeChoiceTrip trip,
            String mode,
            List<String> previousModes) {
        // Geometry check requires the routed sub-legs; handled in validateAfterEstimation.
        return true;
    }

    @Override
    public boolean validateAfterEstimation(
            DiscreteModeChoiceTrip trip,
            TripCandidate candidate,
            List<TripCandidate> previousCandidates) {

        if (!FEEDER_MODE.equals(candidate.getMode())) {
            return true;
        }

        if (!(candidate instanceof RoutedTripCandidate)) {
            // Can't validate without routed elements – reject conservatively.
            return false;
        }

        List<? extends PlanElement> elements = ((RoutedTripCandidate) candidate).getRoutedPlanElements();

        for (int i = 0; i < elements.size(); i++) {
            PlanElement element = elements.get(i);

            if (!(element instanceof Leg)) {
                continue;
            }

            Leg leg = (Leg) element;

            if (!DRT_MODE.equals(leg.getMode())) {
                continue;
            }

            // The activity immediately before this leg is the DRT pickup point.
            // The activity immediately after is the DRT dropoff point.
            Activity pickup  = findPrecedingActivity(elements, i);
            Activity dropoff = findFollowingActivity(elements, i);

            if (pickup == null || dropoff == null) {
                // Malformed trip – reject to avoid NPE downstream.
                return false;
            }

            if (!isInsideServiceArea(pickup) || !isInsideServiceArea(dropoff)) {
                return false;
            }
        }

        return true;
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    /**
     * Returns true when the activity coordinate lies within the service area polygon.
     * Uses the link coordinate as fallback when the activity coordinate is absent.
     */
    private boolean isInsideServiceArea(Activity activity) {
        double x, y;

        if (activity.getCoord() != null) {
            x = activity.getCoord().getX();
            y = activity.getCoord().getY();
        } else if (activity.getLinkId() != null) {
            Link link = network.getLinks().get(activity.getLinkId());
            x = link.getCoord().getX();
            y = link.getCoord().getY();
        } else {
            // No spatial information available – reject conservatively.
            return false;
        }

        Point point = geometryFactory.createPoint(new Coordinate(x, y));
        return serviceArea.covers(point);
    }

    private Activity findPrecedingActivity(List<? extends PlanElement> elements, int legIndex) {
        for (int i = legIndex - 1; i >= 0; i--) {
            if (elements.get(i) instanceof Activity) {
                return (Activity) elements.get(i);
            }
        }
        return null;
    }

    private Activity findFollowingActivity(List<? extends PlanElement> elements, int legIndex) {
        for (int i = legIndex + 1; i < elements.size(); i++) {
            if (elements.get(i) instanceof Activity) {
                return (Activity) elements.get(i);
            }
        }
        return null;
    }

    // -----------------------------------------------------------------------
    // Factory
    // -----------------------------------------------------------------------

    public static class Factory implements TripConstraintFactory {

        private final Network network;
        private final PreparedGeometry serviceArea;

        /**
         * Inject the shapefile path via a Named binding.
         * Bind it in your module:
         *
         *   bind(String.class)
         *       .annotatedWith(Names.named("drt.serviceAreaShapeFile"))
         *       .toInstance("polygon_definition.shp");
         */
        @Inject
        public Factory(
                Network network,
                @Named("drt.serviceAreaShapeFile") String shapeFilePath) {
            this.network = network;
            this.serviceArea = loadServiceArea(shapeFilePath);
        }

        @Override
        public TripConstraint createConstraint(
                Person person,
                List<DiscreteModeChoiceTrip> trips,
                Collection<String> availableModes) {
            return new FeederDrtServiceAreaConstraint(network, serviceArea);
        }

        private static PreparedGeometry loadServiceArea(String path) {
            Collection<SimpleFeature> features = GeoFileReader.getAllFeatures(path);

            Geometry union = null;
            for (SimpleFeature feature : features) {
                Geometry geom = (Geometry) feature.getDefaultGeometry();
                if (union == null) {
                    union = geom;
                } else {
                    union = union.union(geom);
                }
            }

            if (union == null) {
                throw new RuntimeException("No features found in service area shapefile: " + path);
            }

            return PreparedGeometryFactory.prepare(union);
        }
    }
}
