import com.arcrobotics.ftclib.purepursuit.Path;
import com.arcrobotics.ftclib.purepursuit.Waypoint;
import com.arcrobotics.ftclib.purepursuit.actions.InterruptAction;
import com.arcrobotics.ftclib.purepursuit.waypoints.EndWaypoint;
import com.arcrobotics.ftclib.purepursuit.waypoints.GeneralWaypoint;
import com.arcrobotics.ftclib.purepursuit.waypoints.InterruptWaypoint;
import com.arcrobotics.ftclib.purepursuit.waypoints.PointTurnWaypoint;
import com.arcrobotics.ftclib.purepursuit.waypoints.StartWaypoint;

public class Example1 {
	
	
	public void runOpMode() {
		
		// Pure Pursuit Path is declared here.
		Waypoint p1 = new StartWaypoint(0, 0); // Every path begins with a start waypoint.
		Waypoint p2 = new GeneralWaypoint(1, 1, 0, 0.5, 0.5, 2); // General waypoints are just regular waypoints.
		Waypoint p3 = new GeneralWaypoint(2, 2, 0, 1, 1, 2);
		Waypoint p4 = new GeneralWaypoint(2, 3, 0, 1, 2, 1);
		Waypoint p5 = new InterruptWaypoint(2, 2, 0, 1, 1, 2, 0.1, 0.1, new InterruptAction() { 
			// Interrupt waypoints make the robot do an action when they are reached.
			@Override
			public void doAction() {
				grabBlock();
			}
		});
		Waypoint p6 = new GeneralWaypoint(3, 3, 0, 1, 1, 2);
		Waypoint p7 = new PointTurnWaypoint(4, 5, 2, 1, 2, 1, 0.1, 0.1); // Robot will perform a point turn at point turn waypoints.
		Waypoint p8 = new EndWaypoint(8, 8, 0, 1, 1, 2, 0.1, 0.1); // Every path ends with an end waypoint.
		
		// Add all points to a path. Path is a subclass of ArrayList<Waypoint>.
		Path path = new Path(p1, p2, p3, p4, p5, p6, p7, p8);
		
		// This is optional: set a timeout for the path. You can also set timeouts for individual waypoints.
		path.setPathTimeout(30000);
		
		// If the robot loses the path and retrace is enabled, the robot will try to re-find the path.
		path.enableRetrace(); // This is enabled by default.
		path.setRetraceSettings(0.7, 0.7); // Movement and turn speeds while retracing.
		
		// isLegalPath() makes sure the path is valid. 
		if (path.isLegalPath())
			path.init(); // Init the path (Makes sure everything is in order and throws exceptions if not).
		
		waitForStart();
		
		// Stuff before here
		
		while(opModeIsActive() && !path.isFinished()) { // While the robot is still following the path.
			
			// Call the loop method to get the motor powers.
			double[] motorPowers = path.loop(robotXPosition, robotYPosition, robotRotation); 
			
			double xPower = motorPowers[0];
			double yPower = motorPowers[1];
			double turnPower = motorPowers[2];
				
			updateMotorPowers(xPower, yPower, turnPower);
			
		}
		
		// Stuff after here
		
	}
	
	
	
	
	
	
	// Placeholders.
	
	public void updateMotorPowers(double x, double y, double turn) {}
	
	public void waitForStart() {}
	
	public boolean opModeIsActive() { return true; }
	
	public void grabBlock() {}
	
	private double robotXPosition = 0;
	private double robotYPosition = 0;
	private double robotRotation = 0;
	
}
