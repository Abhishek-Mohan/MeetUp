package ca.ubc.cs.cpsc210.meetup.map;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Fragment;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import org.json.JSONArray;
import org.osmdroid.DefaultResourceProxyImpl;
import org.osmdroid.ResourceProxy;
import org.osmdroid.api.IGeoPoint;
import org.osmdroid.api.IMapController;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.ItemizedIconOverlay;
import org.osmdroid.views.overlay.OverlayItem;
import org.osmdroid.views.overlay.OverlayManager;
import org.osmdroid.views.overlay.PathOverlay;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.SortedSet;
import java.io.Reader;
import java.util.TreeSet;


import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

import ca.ubc.cs.cpsc210.meetup.model.EatingPlace;
import ca.ubc.cs.cpsc210.meetup.model.Place;
import ca.ubc.cs.cpsc210.meetup.model.PlaceFactory;
import ca.ubc.cs.cpsc210.meetup.util.LatLon;


import ca.ubc.cs.cpsc210.meetup.R;
import ca.ubc.cs.cpsc210.meetup.model.Building;
import ca.ubc.cs.cpsc210.meetup.model.Course;
import ca.ubc.cs.cpsc210.meetup.model.CourseFactory;
import ca.ubc.cs.cpsc210.meetup.model.PlaceFactory;
import ca.ubc.cs.cpsc210.meetup.model.Schedule;
import ca.ubc.cs.cpsc210.meetup.model.Section;
import ca.ubc.cs.cpsc210.meetup.model.Student;
import ca.ubc.cs.cpsc210.meetup.model.StudentManager;
import ca.ubc.cs.cpsc210.meetup.util.LatLon;
import ca.ubc.cs.cpsc210.meetup.util.SchedulePlot;


/**
 * Fragment holding the map in the UI.
 */
public class MapDisplayFragment extends Fragment
{

    /**
     * Log tag for LogCat messages
     */
    private final static String LOG_TAG = "MapDisplayFragment";

    /**
     * Preference manager to access user preferences
     */
    private SharedPreferences sharedPreferences;

    /**
     * String to know whether we are dealing with MWF or TR schedule.
     * You will need to update this string based on the settings dialog at appropriate
     * points in time. See the project page for details on how to access
     * the value of a setting.
     */
    private String activeDay = "MWF";

    /**
     * A central location in campus that might be handy.
     */
    private final static GeoPoint UBC_MARTHA_PIPER_FOUNTAIN = new GeoPoint(49.264865,
            -123.252782);

    /**
     * Meetup Service URL
     * CPSC 210 Students: Complete the string.
     */
    private final String getStudentURL = "http://kramer.nss.cs.ubc.ca:8081/getStudent";


    /**
     * FourSquare URLs. You must complete the client_id and client_secret with values
     * you sign up for.
     */
    private static String FOUR_SQUARE_URL = "https://api.foursquare.com/v2/venues/explore";
    private static String FOUR_SQUARE_CLIENT_ID = "DXKMBGXUPU0GGDWOG2KU5CACYHBQME2ANX4EKQY5L0K5XYHK";
    private static String FOUR_SQUARE_CLIENT_SECRET = "IXRCK3RV4BB4M3NLWKO2PWQOYC0B1DMUAT31M5I4EOEXBTT0";


    /**
     * Overlays for displaying my schedules, buildings, etc.
     */
    private List<PathOverlay> scheduleOverlay;
    private ItemizedIconOverlay<OverlayItem> buildingOverlay;
    private OverlayItem selectedBuildingOnMap;

    /**
     * View that shows the map
     */
    private MapView mapView;

    /**
     * Access to domain model objects. Only store "me" in the studentManager for
     * the base project (i.e., unless you are doing bonus work).
     */
    public StudentManager studentManager;
    private Student randomStudent = null;
    private Student me = null;
    private static int ME_ID = 41374142;


    /**
     * Map controller for zooming in/out, centering
     */
    private IMapController mapController;

    // ******************** Android methods for starting, resuming, ...

    // You should not need to touch this method
    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        setHasOptionsMenu(true);
        sharedPreferences = PreferenceManager
                .getDefaultSharedPreferences(getActivity());
        scheduleOverlay = new ArrayList<PathOverlay>();

        // You need to setup the courses for the app to know about. Ideally
        // we would access a web service like the UBC student information system
        // but that is not currently possible
        initializeCourses();

        // Initialize the data for the "me" schedule. Note that this will be
        // hard-coded for now
        initializeMySchedule();

        // You are going to need an overlay to draw buildings and locations on the map
        buildingOverlay = createBuildingOverlay();
    }

    // You should not need to touch this method
    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode != Activity.RESULT_OK)
            return;
    }

    // You should not need to touch this method
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        if (mapView == null) {
            mapView = new MapView(getActivity(), null);

            mapView.setTileSource(TileSourceFactory.MAPNIK);
            mapView.setClickable(true);
            mapView.setBuiltInZoomControls(true);
            mapView.setMultiTouchControls(true);

            mapController = mapView.getController();
            mapController.setZoom(mapView.getMaxZoomLevel() - 2);
            mapController.setCenter(UBC_MARTHA_PIPER_FOUNTAIN);
        }

        return mapView;
    }

    // You should not need to touch this method
    @Override
    public void onDestroyView() {
        Log.d(LOG_TAG, "onDestroyView");
        ((ViewGroup) mapView.getParent()).removeView(mapView);
        super.onDestroyView();
    }

    // You should not need to touch this method
    @Override
    public void onDestroy() {
        Log.d(LOG_TAG, "onDestroy");
        super.onDestroy();
    }

    // You should not need to touch this method
    @Override
    public void onResume() {
        Log.d(LOG_TAG, "onResume");
        super.onResume();
    }

    // You should not need to touch this method
    @Override
    public void onPause() {
        Log.d(LOG_TAG, "onPause");
        super.onPause();
    }

    /**
     * Save map's zoom level and centre. You should not need to
     * touch this method
     */
    @Override
    public void onSaveInstanceState(Bundle outState) {
        Log.d(LOG_TAG, "onSaveInstanceState");
        super.onSaveInstanceState(outState);

        if (mapView != null) {
            outState.putInt("zoomLevel", mapView.getZoomLevel());
            IGeoPoint cntr = mapView.getMapCenter();
            outState.putInt("latE6", cntr.getLatitudeE6());
            outState.putInt("lonE6", cntr.getLongitudeE6());
            Log.i("MapSave", "Zoom: " + mapView.getZoomLevel());
        }
    }

    // ****************** App Functionality

    /**
     * Show my schedule on the map. Every time "me"'s schedule shows, the map
     * should be cleared of all existing schedules, buildings, meetup locations, etc.
     */
    public void showMySchedule()
    {
        // CPSC 210 Students: You must complete the implementation of this method.
        // The very last part of the method should call the asynchronous
        // task (which you will also write the code for) to plot the route
        // for "me"'s schedule for the day of the week set in the Settings

        // Asynchronous tasks are a bit onerous to deal with. In order to provide
        // all information needed in one object to plot "me"'s route, we
        // create a SchedulePlot object and pass it to the asynchrous task.
        // See the project page for more details.


        // Get a routing between these points. This line of code creates and calls
        // an asynchronous task to do the calls to MapQuest to determine a route
        // and plots the route.
        // Assumes mySchedulePlot is a create and initialized SchedulePlot object

        // UNCOMMENT NEXT LINE ONCE YOU HAVE INSTANTIATED mySchedulePlot

        clearSchedules();
        String dayOfWeek = sharedPreferences.getString("dayOfWeek", "MWF");
        Schedule myschedule = me.getSchedule();

        SortedSet<Section> myscheduleSections = myschedule.getSections(dayOfWeek);
        SchedulePlot mySchedulePlot = new SchedulePlot(myscheduleSections, me.getFirstName(), "#FF0000", R.drawable.ic_action_place);

        //plotBuildings(mySchedulePlot);

        new GetRoutingForSchedule().execute(mySchedulePlot);
    }

    /**
     * Retrieve a random student's schedule from the Meetup web service and
     * plot a route for the schedule on the map. The plot should be for
     * the given day of the week as determined when "me"'s schedule
     * was plotted.
     */
    public void showRandomStudentsSchedule()
    {
        clearSchedules();

        /*
        String dayOfWeek = sharedPreferences.getString("dayOfWeek", "MWF");
        Schedule randomschedule = randomStudent.getSchedule();

        SortedSet<Section> randomscheduleSections = randomschedule.getSections(dayOfWeek);
        SchedulePlot randomSchedulePlot = new SchedulePlot(randomscheduleSections, randomStudent.getFirstName(), "Blue", R.drawable.ic_action_place);

        plotBuildings(randomSchedulePlot);
        */


        // To get a random student's schedule, we have to call the MeetUp web service.
        // Calling this web service requires a network access to we have to
        // do this in an asynchronous task. See below in this class for where
        // you need to implement methods for performing the network access
        // and plotting.
        new GetRandomSchedule().execute();
    }

    /**
     * Clear all schedules on the map
     */
    public void clearSchedules() {
        randomStudent = null;
        OverlayManager om = mapView.getOverlayManager();
        om.clear();
        scheduleOverlay.clear();
        buildingOverlay.removeAllItems();
        om.addAll(scheduleOverlay);
        om.add(buildingOverlay);
        mapView.invalidate();
    }

    /**
     * Find all possible locations at which "me" and random student could meet
     * up for the set day of the week and the set time to meet and the set
     * distance either "me" or random is willing to travel to meet.
     * A meetup is only possible if both "me" and random are free at the
     * time specified in the settings and each of us must have at least an hour
     * (>= 60 minutes) free. You should display dialog boxes if there are
     * conditions under which no meetup can happen (e.g., me or random is
     * in class at the specified time)
     */
    public void findMeetupPlace()
    {
        String dayOfWeek = sharedPreferences.getString("dayOfWeek", "MWF");
        String timeofDay = sharedPreferences.getString("timeOfDay", "10");
        String radius = sharedPreferences.getString("placeDistance", "250");
        int radiusInt = Integer.parseInt(radius);
        PlaceFactory pf = PlaceFactory.getInstance();


        if (me.getSchedule().getStartTimesOfBreaks(dayOfWeek).contains(randomStudent.getSchedule().getStartTimesOfBreaks(timeofDay)))
        {


            Set<Place> meclosePlaces = pf.findPlacesWithinDistance(new LatLon(me.getSchedule().whereAmI(dayOfWeek,timeofDay).getLatLon().getLatitude(), me.getSchedule().whereAmI(dayOfWeek, timeofDay).getLatLon().getLongitude()), radiusInt);
            Set<Place> randclosePlaces = pf.findPlacesWithinDistance(new LatLon(randomStudent.getSchedule().whereAmI(dayOfWeek, timeofDay).getLatLon().getLatitude(), randomStudent.getSchedule().whereAmI(dayOfWeek, timeofDay).getLatLon().getLongitude()), radiusInt);


            //if (meclosePlaces.equals(randclosePlaces))
            //{
                Set<Place> plotthem = pf.findPlacesWithinDistance(new LatLon(me.getSchedule().whereAmI(dayOfWeek,
                        timeofDay).getLatLon().getLatitude(), me.getSchedule().whereAmI(dayOfWeek, timeofDay).getLatLon().getLongitude()), radiusInt);
                for (Place p : plotthem)
                {
                    plotABuilding(new Building(p.getName(), new LatLon(p.getLatLon().getLatitude(), p.getLatLon().getLongitude())), p.getName(), "eat here plz", R.drawable.ic_action_pizza);
                }
                //plotABuilding(new Building(name, new LatLon(lat, lon)), name, "eat here plz", R.drawable.ic_action_pizza);
            //}
            //else
            //{
              //  AlertDialog box1 = createSimpleDialog("nothing close by");
               /// box1.show();
            //}



        }
        else
        {
            for (String s : me.getSchedule().getStartTimesOfBreaks(dayOfWeek))
            {
                Log.i(LOG_TAG, s);
            }
            Log.i(LOG_TAG, "space");
            for (String p : randomStudent.getSchedule().getStartTimesOfBreaks(dayOfWeek))
            {
                Log.i(LOG_TAG, p);
            }

            AlertDialog box2 = createSimpleDialog("no times to meet up");
            box2.show();
        }





        OverlayManager om = mapView.getOverlayManager();
        om.add(buildingOverlay);




    }

    /**
     * Initialize the PlaceFactory with information from FourSquare
     */
    public void initializePlaces() {
        // CPSC 210 Students: You should not need to touch this method, but
        // you will have to implement GetPlaces below.
        new GetPlaces().execute();
    }


    /**
     * Plot all buildings referred to in the given information about plotting
     * a schedule.
     * @param schedulePlot All information about the schedule and route to plot.
     */
    private void plotBuildings(SchedulePlot schedulePlot)
    {
        SortedSet<Section> sections = schedulePlot.getSections();

        for (Section s : sections)
        {
            plotABuilding(s.getBuilding(), s.getCourse().toString(), s.getName(), R.drawable.ic_action_place);
        }

        // CPSC 210 Students: Complete this method by plotting each building in the
        // schedulePlot with an appropriate message displayed
        // CPSC 210 Students: You will need to ensure the buildingOverlay is in
        // the overlayManager. The following code achieves this. You should not likely
        // need to touch it
        OverlayManager om = mapView.getOverlayManager();
        om.add(buildingOverlay);

    }

    /**
     * Plot a building onto the map
     * @param building The building to put on the map
     * @param title The title to put in the dialog box when the building is tapped on the map
     * @param msg The message to display when the building is tapped
     * @param drawableToUse The icon to use. Can be R.drawable.ic_action_place (or any icon in the res/drawable directory)
     */
    private void plotABuilding(Building building, String title, String msg, int drawableToUse) {
        // CPSC 210 Students: You should not need to touch this method
        OverlayItem buildingItem = new OverlayItem(title, msg,
                new GeoPoint(building.getLatLon().getLatitude(), building.getLatLon().getLongitude()));

        //Create new marker
        Drawable icon = this.getResources().getDrawable(drawableToUse);

        //Set the bounding for the drawable
        icon.setBounds(
                0 - icon.getIntrinsicWidth() / 2, 0 - icon.getIntrinsicHeight(),
                icon.getIntrinsicWidth() / 2, 0);

        //Set the new marker to the overlay
        buildingItem.setMarker(icon);
        buildingOverlay.addItem(buildingItem);
    }



    /**
     * Initialize your schedule by coding it directly in. This is the schedule
     * that will appear on the map when you select "Show My Schedule".
     */
    private void initializeMySchedule()
    {
        this.studentManager = new StudentManager();
        studentManager.addStudent("Mohan", "Abhishek", ME_ID);
        //studentManager.addSectionToSchedule(ME_ID, "CPSC", 210, "BCS");
        //studentManager.addSectionToSchedule(ME_ID, "ENGL", 222, "007");
        //studentManager.addSectionToSchedule(ME_ID, "SCIE", 220, "200");
        //studentManager.addSectionToSchedule(ME_ID, "MATH", 200, "201");
        //studentManager.addSectionToSchedule(ME_ID, "FREN", 102, "202");
        //studentManager.addSectionToSchedule(ME_ID, "PHYS", 203, "201");
        studentManager.addSectionToSchedule(ME_ID, "CHEM", 250, "203");
        studentManager.addSectionToSchedule(ME_ID, "BIOL", 201, "201");
        //studentManager.addSectionToSchedule(ME_ID, "CPSC", 430, "201");
        me = studentManager.get(ME_ID);


    }

    /**
     * Helper to create simple alert dialog to display message
     *
     * @param msg message to display in alert dialog
     * @return the alert dialog
     */
    private AlertDialog createSimpleDialog(String msg)
    {
        // CPSC 210 Students; You should not need to modify this method
        AlertDialog.Builder dialogBldr = new AlertDialog.Builder(getActivity());
        dialogBldr.setMessage(msg);
        dialogBldr.setNeutralButton(R.string.ok, null);

        return dialogBldr.create();
    }

    /**
     * Create the overlay used for buildings. CPSC 210 students, you should not need to
     * touch this method.
     * @return An overlay
     */
    private ItemizedIconOverlay<OverlayItem> createBuildingOverlay() {
        ResourceProxy rp = new DefaultResourceProxyImpl(getActivity());

        ItemizedIconOverlay.OnItemGestureListener<OverlayItem> gestureListener =
                new ItemizedIconOverlay.OnItemGestureListener<OverlayItem>() {

            /**
             * Display building description in dialog box when user taps stop.
             *
             * @param index
             *            index of item tapped
             * @param oi
             *            the OverlayItem that was tapped
             * @return true to indicate that tap event has been handled
             */
            @Override
            public boolean onItemSingleTapUp(int index, OverlayItem oi) {

                new AlertDialog.Builder(getActivity())
                        .setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface arg0, int arg1) {
                                if (selectedBuildingOnMap != null) {
                                    mapView.invalidate();
                                }
                            }
                        }).setTitle(oi.getTitle()).setMessage(oi.getSnippet())
                        .show();

                selectedBuildingOnMap = oi;
                mapView.invalidate();
                return true;
            }

            @Override
            public boolean onItemLongPress(int index, OverlayItem oi) {
                // do nothing
                return false;
            }
        };

        return new ItemizedIconOverlay<OverlayItem>(
                new ArrayList<OverlayItem>(), getResources().getDrawable(
                R.drawable.ic_action_place), gestureListener, rp);
    }


    /**
     * Create overlay with a specific color
     * @param colour A string with a hex colour value
     */
    private PathOverlay createPathOverlay(String colour) {
        // CPSC 210 Students, you should not need to touch this method
        PathOverlay po = new PathOverlay(Color.parseColor(colour),
                getActivity());
        Paint pathPaint = new Paint();
        pathPaint.setColor(Color.parseColor(colour));
        pathPaint.setStrokeWidth(4.0f);
        pathPaint.setStyle(Paint.Style.STROKE);
        po.setPaint(pathPaint);
        return po;
    }

   // *********************** Asynchronous tasks

    /**
     * This asynchronous task is responsible for contacting the Meetup web service
     * for the schedule of a random student. The task must plot the retrieved
     * student's route for the schedule on the map in a different colour than the "me" schedule
     * or must display a dialog box that a schedule was not retrieved.
     */
    private class GetRandomSchedule extends AsyncTask<Void, Void, SchedulePlot> {

        // Some overview explanation of asynchronous tasks is on the project web page.

        @Override
        protected void onPreExecute()
        {

        }

        @Override
        protected SchedulePlot doInBackground(Void... params)
        {
            SortedSet<Section> randomstudentsections = new TreeSet<>();
            CourseFactory cf = CourseFactory.getInstance();
            String dayOfWeek = sharedPreferences.getString("dayOfWeek", "MWF");
            String firstName = "";
            String lastName = "";
            int randID = 0;
            studentManager = new StudentManager();




            try
            {
                String response = makeRoutingCall(getStudentURL);
                JSONObject jsonObject = new JSONObject(new JSONTokener(response));

                if (jsonObject.length() == 0)
                {
                    createSimpleDialog("no student found");
                    //return null;
                }
                else
                {

                    firstName = jsonObject.getString("FirstName");
                    lastName = jsonObject.getString("LastName");
                    randID = jsonObject.getInt("Id");
                    studentManager.addStudent(firstName, lastName, randID);

                    Log.i(LOG_TAG, firstName);

                    JSONArray sections = jsonObject.getJSONArray("Sections");
                    for (int i = 0; i < sections.length(); i++)
                    {
                        JSONObject sections1 = (JSONObject) sections.get(i);
                        String courseName = sections1.getString("CourseName");
                        Log.i(LOG_TAG, courseName);
                        String courseNumber = sections1.getString("CourseNumber");
                        int courseNumberInt = Integer.parseInt(courseNumber);
                        Log.i(LOG_TAG, courseNumber);
                        String sectionName = sections1.getString("SectionName");
                        Log.i(LOG_TAG, sectionName);
                        if (cf.getCourse(courseName, courseNumberInt).getSection(sectionName).getDayOfWeek().equals(dayOfWeek))
                        {
                            randomstudentsections.add(cf.getCourse(courseName, courseNumberInt).getSection(sectionName));
                            studentManager.addSectionToSchedule(randID, courseName, courseNumberInt, sectionName);
                            //randSchedule.add(new Section(sectionName, dayOfWeek, cf.getCourse(courseName,
                              //      foo).getSection(sectionName).getCourseTime().getStartTime(), cf.getCourse(courseName,
                                //    foo).getSection(sectionName).getCourseTime().getEndTime(), cf.getCourse(courseName, foo).getSection(courseName).getBuilding()));
                        }
                    }
                }
            }
            catch (IOException e)
            {
                e.printStackTrace();
            }
            catch (JSONException e)
            {
                e.printStackTrace();
            }

            randomStudent = studentManager.get(randID);



            SchedulePlot schedulePlot = new SchedulePlot(randomstudentsections, firstName, "blue", R.drawable.ic_action_place);

            return new GetRoutingForSchedule().doInBackground(schedulePlot);

            // CPSC 210 Students: You must complete this method. It needs to
            // contact the Meetup web service to get a random student's schedule.
            // If it is successful in retrieving a student and their schedule,
            // it needs to remember the student in the randomStudent field
            // and it needs to create and return a schedulePlot object with
            // all relevant information for being ready to retrieve the route
            // and plot the route for the schedule. If no random student is
            // retrieved, return null.
            //
            // Note, leave all determination of routing and plotting until
            // the onPostExecute method below.

        }

        @Override
        protected void onPostExecute(SchedulePlot schedulePlot)
        {
            String dayOfWeek = sharedPreferences.getString("dayOfWeek", "MWF");

            if (randomStudent.getSchedule().getSections(dayOfWeek).size() == 0)
            {
                AlertDialog randnoclass = createSimpleDialog("Random Student has no classes today");
                randnoclass.show();
            }

            List<GeoPoint> points = schedulePlot.getRoute();



            PathOverlay po = createPathOverlay("#0000FF"); // blue

            for (GeoPoint point : points)
            {
                po.addPoint(point);
            }
            scheduleOverlay.add(po);
            OverlayManager om = mapView.getOverlayManager();
            om.addAll(scheduleOverlay);
            plotBuildings(schedulePlot);
            mapView.invalidate(); // cause map to redraw

            // CPSC 210 students: When this method is called, it will be passed
            // whatever schedulePlot object you created (if any) in doBackground
            // above. Use it to plot the route.
        }
    }

    /**
     * This asynchronous task is responsible for contacting the MapQuest web service
     * to retrieve a route between the buildings on the schedule and for plotting any
     * determined route on the map.
     */
    private class GetRoutingForSchedule extends AsyncTask<SchedulePlot, Void, SchedulePlot>
    {

        @Override
        protected void onPreExecute()
        {

        }
        @Override
        protected SchedulePlot doInBackground(SchedulePlot... params)
        {
            SchedulePlot scheduleToPlot = params[0];

            ArrayList<GeoPoint> points = new ArrayList<GeoPoint>();
            SortedSet<Section> mysections = scheduleToPlot.getSections();

            double lat1;
            double lon1;
            double lat2;
            double lon2;

            String response;

            if (mysections.size() > 1)
            {
                Iterator<Section> itr = mysections.iterator();

                Section prev = itr.next();

                while (itr.hasNext()) {
                    lat1 = prev.getBuilding().getLatLon().getLatitude();
                    lon1 = prev.getBuilding().getLatLon().getLongitude();

                    Section next = itr.next();

                    lat2 = next.getBuilding().getLatLon().getLatitude();
                    lon2 = next.getBuilding().getLatLon().getLongitude();

                    String request = "http://open.mapquestapi.com/" +
                            "directions/v2/route?key=Fmjtd%7Cluu82l0al1%2C2a%3Do5-94zn5y&callback=ren" +
                            "derAdvancedNarrative&outFormat=jso" +
                            "n&routeType=pedestrian&timeType=1&" +
                            "enhancedNarrative=false&shapeForma" +
                            "t=raw&generalize=0&locale=en_US&un" +
                            "it=m&from=" + Double.toString(lat1) + "," + Double.toString(lon1) + "&to=" + Double.toString(lat2) + "," + Double.toString(lon2) + "&drivingStyle=2&highwayEfficiency=21.0";

                    try
                    {
                        response = makeRoutingCall(request);
                        response = response.substring(24, response.length() - 1);

                        //Log.i(LOG_TAG, request);

                        JSONObject jsonObject = new JSONObject(new JSONTokener(response));
                        JSONObject direction = (JSONObject) jsonObject.get("route");
                        JSONObject shape = (JSONObject) direction.get("shape");
                        JSONArray locations = shape.getJSONArray("shapePoints");

                        double lat;
                        double lon;

                        for (int i = 0; i < locations.length(); i += 2)
                        {
                            lat = (double) locations.get(i);
                            lon = (double) locations.get(i + 1);

                            GeoPoint geodude1 = new GeoPoint(lat, lon);
                            points.add(geodude1);

                        }
                    } catch (MalformedURLException e) {
                        e.printStackTrace();
                    } catch (IOException e) {
                        e.printStackTrace();
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }

                    prev = next;

                }
            }

                scheduleToPlot.setRoute(points);
                return scheduleToPlot;

        }

        @Override
        protected void onPostExecute(SchedulePlot schedulePlot)
        {

            if (schedulePlot.getRoute().size() == 0)
            {
                createSimpleDialog("no routes here, move along");
            }

            List<GeoPoint> points = schedulePlot.getRoute();

            PathOverlay po = createPathOverlay("#FF0000");   // Red

            for (GeoPoint point : points)
            {
                po.addPoint(point);
            }

            scheduleOverlay.add(po);
            //scheduleOverlay.add(po1);
            //scheduleOverlay.add(po2);
            OverlayManager om = mapView.getOverlayManager();
            om.addAll(scheduleOverlay);
            plotBuildings(schedulePlot);
            mapView.invalidate(); // cause map to redraw


            // CPSC 210 Students: This method should plot the route onto the map
            // with the given line colour specified in schedulePlot. If there is
            // no route to plot, a dialog box should be displayed.

            // To actually make something show on the map, you can use overlays.
            // For instance, the following code should show a line on a map
            // PathOverlay po = createPathOverlay("#FFFFFF");
            // po.addPoint(point1); // one end of line
            // po.addPoint(point2); // second end of line
            // scheduleOverlay.add(po);
            // OverlayManager om = mapView.getOverlayManager();
            // om.addAll(scheduleOverlay);
            // mapView.invalidate(); // cause map to redraw

        }

    }



    /**
     * An example helper method to call a web service
     */
    private String makeRoutingCall(String httpRequest) throws MalformedURLException, IOException
    {
        URL url = new URL(httpRequest);
        HttpURLConnection client = (HttpURLConnection) url.openConnection();
        InputStream in = client.getInputStream();
        BufferedReader br = new BufferedReader(new InputStreamReader(in));
        String returnString = br.readLine();
        client.disconnect();
        return returnString;
    }

    /**
     * This asynchronous task is responsible for contacting the FourSquare web service
     * to retrieve all places around UBC that have to do with food. It should load
     * any determined places into PlaceFactory and then display a dialog box of how it did
     */
    private class GetPlaces extends AsyncTask<Void, Void, String> {

        protected String doInBackground(Void... params)
        {
            String currentDate = new SimpleDateFormat("yyyyMMdd").format(new Date());
            String dayOfWeek = sharedPreferences.getString("dayOfWeek", "MWF");
            String timeofDay = sharedPreferences.getString("timeOfDay", "10");
            String radius = sharedPreferences.getString("placeDistance", "250");

            String lat = String.valueOf(UBC_MARTHA_PIPER_FOUNTAIN.getLatitude());
            String lon = String.valueOf(UBC_MARTHA_PIPER_FOUNTAIN.getLongitude());

            //Building f = me.getSchedule().whereAmI(dayOfWeek, timeofDay);
            //String lat = String.valueOf(f.getLatLon().getLatitude());
            //String lon = String.valueOf(f.getLatLon().getLongitude());
            //String result = "https://api.foursquare.com/v2/venues/search?client_id=DXKMBGXUPU0GGDWOG2KU5CACYHBQME2ANX4EKQY5L0K5XYHK&client_secret=IXRCK3RV4BB4M3NLWKO2PWQOYC0B1DMUAT31M5I4EOEXBTT0&v=20130815%20&ll=49.264865,-123.252782&section=food&radius=80467.2&limit=50";
            String result = "https://api.foursquare.com/v2/venues/explore?clien" +
                    "t_id=DXKMBGXUPU0GGDWOG2KU5CACYHBQME2ANX4EKQY5L0K5XYHK&cli" +
                    "ent_secret=IXRCK3RV4BB4M3NLWKO2PWQOYC0B1DMUAT31M5I4EOE" +
                    "XBTT0&v=" + currentDate + "%20&ll=" + lat + "," + lon + "&section=food&radius=" + radius;
            Log.i(LOG_TAG, result);
            String response = null;
            try {
                response = makeRoutingCall(result);
            } catch (IOException e) {
                e.printStackTrace();
            }


            // CPSC 210 Students: Complete this method to retrieve a string
            // of JSON from FourSquare. Return the string from this method


            return response;
        }

        protected void onPostExecute(String jSONOfPlaces)
        {
            PlaceFactory pf = PlaceFactory.getInstance();
            List<GeoPoint> points = new ArrayList<GeoPoint>();
            int counter = 0;
            String name = null;
            double lat;
            double lon;

            try
            {
                JSONObject jsonObject = new JSONObject(jSONOfPlaces);
                JSONObject response = (JSONObject) jsonObject.get("response");
                JSONArray groups = response.getJSONArray("groups");
                for (int i = 0; i < groups.length(); i++)
                {
                    JSONObject currentjsonObject = (JSONObject) groups.get(i);
                    JSONArray items = currentjsonObject.getJSONArray("items");
                    for (int j = 0; j < items.length(); j++)
                    {
                        JSONObject currentjsonObject2 = (JSONObject) items.get(j);
                        JSONObject venue = currentjsonObject2.getJSONObject("venue");
                        JSONArray categories = venue.getJSONArray("categories");
                        for (int k = 0; k < categories.length(); k++)
                        {
                            JSONObject currentjsonObject3 = (JSONObject) categories.get(k);
                            JSONObject icon = (JSONObject) currentjsonObject3.get("icon");
                            String prefix = icon.getString("prefix");

                            Log.i(LOG_TAG, prefix);
                            if (prefix.contains("/food/"))
                            {
                                counter++;
                                name = venue.getString("name");
                                Log.i(LOG_TAG, name);
                                JSONObject location = venue.getJSONObject("location");
                                lat = location.getDouble("lat");
                                Log.i(LOG_TAG, String.valueOf(lat));
                                lon = location.getDouble("lng");
                                Log.i(LOG_TAG, String.valueOf(lon));

                                //name = venues.getJSONObject(i).getJSONArray("categories").getJSONObject(i).getString("name");
                                //lat = Double.parseDouble(venues.getJSONObject(i).getJSONObject("location").getString("lat"));
                                //lon = Double.parseDouble(venues.getJSONObject(i).getJSONObject("location").getString("lon"));
                                //points.add(new GeoPoint(lat, lon));
                                pf.add(new Place(name, new LatLon(lat, lon)));
                                //plotABuilding(new Building(name, new LatLon(lat, lon)), name, "eat here plz", R.drawable.ic_action_pizza);
                            }
                        }
                    }
                }
            }
            catch (JSONException e)
            {
                e.printStackTrace();
            }


            // CPSC 210 Students: Given JSON from FourQuest, parse it and load
            // PlaceFactory
            Log.i(LOG_TAG, String.valueOf(counter));

            /*
            for (GeoPoint point : points)
            {
                plotABuilding(new Building(name,new LatLon(point.getLatitude(), point.getLongitude())), name, "eat here plz", R.drawable.ic_action_place);
            }
            */


            //OverlayManager om = mapView.getOverlayManager();
            //om.add(buildingOverlay);


            String places = String.valueOf(counter);
            AlertDialog aDialog = createSimpleDialog("Found " + places + " places to meet and eat!");
            aDialog.show();

        }

    }

    /**
     * Initialize the CourseFactory with some courses.
     */
    private void initializeCourses() {
        // CPSC 210 Students: You can change this data if you desire.
        CourseFactory courseFactory = CourseFactory.getInstance();

        Building dmpBuilding = new Building("DMP", new LatLon(49.261474, -123.248060));

        Course cpsc210 = courseFactory.getCourse("CPSC", 210);
        Section aSection = new Section("202", "MWF", "12:00", "12:50", dmpBuilding);
        cpsc210.addSection(aSection);
        aSection.setCourse(cpsc210);
        aSection = new Section("201", "MWF", "16:00", "16:50", dmpBuilding);
        cpsc210.addSection(aSection);
        aSection.setCourse(cpsc210);
        aSection = new Section("BCS", "MWF", "12:00", "12:50", dmpBuilding);
        cpsc210.addSection(aSection);
        aSection.setCourse(cpsc210);

        Course engl222 = courseFactory.getCourse("ENGL", 222);
        aSection = new Section("007", "MWF", "14:00", "14:50", new Building("Buchanan", new LatLon(49.269258, -123.254784)));
        engl222.addSection(aSection);
        aSection.setCourse(engl222);

        Course scie220 = courseFactory.getCourse("SCIE", 220);
        aSection = new Section("200", "MWF", "15:00", "15:50", new Building("Swing", new LatLon(49.262786, -123.255044)));
        scie220.addSection(aSection);
        aSection.setCourse(scie220);

        Course math200 = courseFactory.getCourse("MATH", 200);
        aSection = new Section("201", "MWF", "09:00", "09:50", new Building("Buchanan", new LatLon(49.269258, -123.254784)));
        math200.addSection(aSection);
        aSection.setCourse(math200);

        Course fren102 = courseFactory.getCourse("FREN", 102);
        aSection = new Section("202", "MWF", "11:00", "11:50", new Building("Barber", new LatLon(49.267442,-123.252471)));
        fren102.addSection(aSection);
        aSection.setCourse(fren102);

        Course japn103 = courseFactory.getCourse("JAPN", 103);
        aSection = new Section("002", "MWF", "10:00", "11:50", new Building("Buchanan", new LatLon(49.269258, -123.254784)));
        japn103.addSection(aSection);
        aSection.setCourse(japn103);

        Course scie113 = courseFactory.getCourse("SCIE", 113);
        aSection = new Section("213", "MWF", "13:00", "13:50", new Building("Swing", new LatLon(49.262786, -123.255044)));
        scie113.addSection(aSection);
        aSection.setCourse(scie113);

        Course micb308 = courseFactory.getCourse("MICB", 308);
        aSection = new Section("201", "MWF", "12:00", "12:50", new Building("Woodward", new LatLon(49.264704,-123.247536)));
        micb308.addSection(aSection);
        aSection.setCourse(micb308);

        Course math221 = courseFactory.getCourse("MATH", 221);
        aSection = new Section("202", "TR", "11:00", "12:20", new Building("Klinck", new LatLon(49.266112, -123.254776)));
        math221.addSection(aSection);
        aSection.setCourse(math221);

        Course phys203 = courseFactory.getCourse("PHYS", 203);
        aSection = new Section("201", "TR", "09:30", "10:50", new Building("Hennings", new LatLon(49.266400,-123.252047)));
        phys203.addSection(aSection);
        aSection.setCourse(phys203);

        Course crwr209 = courseFactory.getCourse("CRWR", 209);
        aSection = new Section("002", "TR", "12:30", "13:50", new Building("Geography", new LatLon(49.266039,-123.256129)));
        crwr209.addSection(aSection);
        aSection.setCourse(crwr209);

        Course fnh330 = courseFactory.getCourse("FNH", 330);
        aSection = new Section("002", "TR", "15:00", "16:20", new Building("MacMillian", new LatLon(49.261167,-123.251157)));
        fnh330.addSection(aSection);
        aSection.setCourse(fnh330);

        Course cpsc499 = courseFactory.getCourse("CPSC", 430);
        aSection = new Section("201", "TR", "16:20", "17:50", new Building("Liu", new LatLon(49.267632,-123.259334)));
        cpsc499.addSection(aSection);
        aSection.setCourse(cpsc499);

        Course chem250 = courseFactory.getCourse("CHEM", 250);
        aSection = new Section("203", "TR", "10:00", "11:20", new Building("Klinck", new LatLon(49.266112, -123.254776)));
        chem250.addSection(aSection);
        aSection.setCourse(chem250);

        Course eosc222 = courseFactory.getCourse("EOSC", 222);
        aSection = new Section("200", "TR", "11:00", "12:20", new Building("ESB", new LatLon(49.262866, -123.25323)));
        eosc222.addSection(aSection);
        aSection.setCourse(eosc222);

        Course biol201 = courseFactory.getCourse("BIOL", 201);
        aSection = new Section("201", "TR", "14:00", "15:20", new Building("BioSci", new LatLon(49.263920, -123.251552)));
        biol201.addSection(aSection);
        aSection.setCourse(biol201);
    }

}
