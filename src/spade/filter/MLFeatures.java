/*
 --------------------------------------------------------------------------------
 SPADE - Support for Provenance Auditing in Distributed Environments.
 Copyright (C) 2012 SRI International

 This program is free software: you can redistribute it and/or
 modify it under the terms of the GNU General Public License as
 published by the Free Software Foundation, either version 3 of the
 License, or (at your option) any later version.

 This program is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 General Public License for more details.

 You should have received a copy of the GNU General Public License
 along with this program. If not, see <http://www.gnu.org/licenses/>.
 --------------------------------------------------------------------------------

 */
package spade.filter;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import spade.core.AbstractEdge;
import spade.core.AbstractFilter;
import spade.core.AbstractVertex;
import spade.utility.CommonFunctions;

/**
 * A filter to drop annotations passed in arguments.
 *
 * Arguments format: keys=name,version,epoch
 *
 * Note 1: Filter relies on two things for successful use of Java Reflection API:
 * 1) The vertex objects passed have an empty constructor
 * 2) The edge objects passed have a constructor with source vertex and a destination vertex (in that order)
 *
 * Note 2: Creating copy of passed vertices and edges instead of just removing the annotations from the existing ones
 * because the passed vertices and edges might be in use by some other classes specifically the reporter that
 * generated them. In future, maybe shift the responsibility of creating a copy to Kernel before it sends out
 * the vertices and edges to filters.
 */
public class MLFeatures extends AbstractFilter{

	private Logger logger = Logger.getLogger(this.getClass().getName());
	private HashMap<String,HashMap<String,Double>> features = new HashMap<>();
	private HashMap<String,String> agentsName = new HashMap<>();
	private HashMap<String,String> firstActivity = new HashMap<>();
	private HashMap<String,String> lastUsed = new HashMap<>();
	private HashMap<String,String> lastWgb = new HashMap<>();
	private static int SCALE_TIME = 10000000;
	private final String USER = "User";
	private final String WCB = "WasControlledBy";
	private final String PROCESS = "Process";
	private final String PROCESS_IDENTIFIER = "pid";
	private final String USED = "Used";
	private final String COUNT_USED = "countUsed";
	private final String WGB = "WasGeneratedBy";
	private final String COUNT_WGB = "countWgb";
	private final String AVG_DURATION_BETWEEN_TWO_USED = "avgDurationBetweenTwoUsed";
	private final String AVG_DURATION_BETWEEN_TWO_WGB = "avgDurationBetweenTwoWgb";
	private final String TIME = "time";
	private final String MEAN_TIME_BETWEEN_TWO_USED = "meanTimeBetweenTwoUsed";
	private final Double INITIAL_ZERO = (double) 0;
	private final String AVG_DURATION_USED = "avgDurationUsed";
	private final String AVG_DURATION_WGB = "avgDurationWgb";
	private final String DURATION = "duration";
	private final String COUNT_FILESYSTEM_USED = "countFilesystemUsed";
	private final String COUNT_FILESYSTEM_WGB = "countFilesystemWgb";
	private final String CLASS = "class";
	private final String FILE_SYSTEM = "File System";
	private final String LIFE_DURATION = "lifeDuration";
	
	public boolean initialize(String arguments){

		return true;

	}

	@Override
	public void putVertex(AbstractVertex incomingVertex) {
		if(incomingVertex != null){
			if(incomingVertex.type() == PROCESS){
				
				String processPid = incomingVertex.getAnnotation(PROCESS_IDENTIFIER);
				HashMap<String,Double> initialFeatures = new HashMap<>();
				initialFeatures.put(COUNT_USED, INITIAL_ZERO);
				initialFeatures.put(COUNT_WGB, INITIAL_ZERO);
				initialFeatures.put(MEAN_TIME_BETWEEN_TWO_USED,INITIAL_ZERO);
				initialFeatures.put(AVG_DURATION_USED,INITIAL_ZERO);
				initialFeatures.put(AVG_DURATION_WGB, INITIAL_ZERO);
				initialFeatures.put(COUNT_FILESYSTEM_USED, INITIAL_ZERO);
				initialFeatures.put(COUNT_FILESYSTEM_WGB, INITIAL_ZERO);
				initialFeatures.put(AVG_DURATION_BETWEEN_TWO_USED,Double.MAX_VALUE);
				initialFeatures.put(AVG_DURATION_BETWEEN_TWO_WGB,Double.MAX_VALUE);
				features.put(processPid,initialFeatures);
				
			}
			putInNextFilter(incomingVertex);
		}else{
			logger.log(Level.WARNING, "Null vertex");
		}
	}

	@Override
	public void putEdge(AbstractEdge incomingEdge) {
		if(incomingEdge != null && incomingEdge.getSourceVertex() != null && incomingEdge.getDestinationVertex() != null){
			
			String time = incomingEdge.getAnnotation(TIME);
			
			if (incomingEdge.getSourceVertex().type() == PROCESS) {
				
				AbstractVertex sourceProcessVertex = incomingEdge.getSourceVertex();
				AbstractVertex destinationVertex = incomingEdge.getDestinationVertex();
				String ProcessPid = sourceProcessVertex.getAnnotation(PROCESS_IDENTIFIER);
				HashMap<String, Double> sourceProcess = features.get(ProcessPid);
				
				if (incomingEdge.type() == USED){
					
					double count_used = sourceProcess.get(COUNT_USED);
					
					if(!firstActivity.containsKey(ProcessPid)){
						firstActivity.put(ProcessPid, time);
					}
					
					if (count_used == 0){
						lastUsed.put(ProcessPid, time);
					}else{
						double avgDurationBetweenTwoUsed = sourceProcess.get(AVG_DURATION_BETWEEN_TWO_USED);
						String lastTimeUsed = lastUsed.get(ProcessPid);
						sourceProcess.put(AVG_DURATION_BETWEEN_TWO_USED,(avgDurationBetweenTwoUsed*(count_used-1) + differenceBetweenTwoTimes(time, lastTimeUsed))/count_used );
					}
						
					sourceProcess.put(LIFE_DURATION, differenceBetweenTwoTimes(firstActivity.get(ProcessPid),time));
					

					
					
					double duration = Double.parseDouble(incomingEdge.getAnnotation(DURATION));
					double currentDurationMean = sourceProcess.get(AVG_DURATION_USED);
					
					sourceProcess.put(AVG_DURATION_USED,(currentDurationMean*count_used + duration)/(count_used+1) );
					sourceProcess.put(COUNT_USED, count_used + 1);
					
					if(destinationVertex.getAnnotation(CLASS) == FILE_SYSTEM){
						double count_filesystem = sourceProcess.get(COUNT_FILESYSTEM_USED);
						sourceProcess.put(COUNT_FILESYSTEM_USED, count_filesystem + 1);
					}
					
				}else if (incomingEdge.type() == WCB){
					agentsName.put(PROCESS_IDENTIFIER,incomingEdge.getDestinationVertex().getAnnotation(USER));
				}
				
				
				
				
				
				
			}else if (incomingEdge.getDestinationVertex().type() == PROCESS){	
				
				AbstractVertex destinationProcessVertex = incomingEdge.getDestinationVertex();
				AbstractVertex sourceVertex = incomingEdge.getSourceVertex();
				String ProcessPid = destinationProcessVertex.getAnnotation(PROCESS_IDENTIFIER);
				HashMap<String, Double> destinationProcess = features.get(ProcessPid);
				
				if(!firstActivity.containsKey(ProcessPid)){
					firstActivity.put(ProcessPid, time);
				}
				
				destinationProcess.put(LIFE_DURATION, differenceBetweenTwoTimes(firstActivity.get(ProcessPid),time));
				
				if (incomingEdge.type() == WGB){
					
					double count_wgb = destinationProcess.get(COUNT_WGB);
					
					if (count_wgb == 0){
						lastWgb.put(ProcessPid, time);
					}else{
						double avgDurationBetweenTwoWgb = destinationProcess.get(AVG_DURATION_BETWEEN_TWO_WGB);
						String lastTimeWgb = lastWgb.get(ProcessPid);
						destinationProcess.put(AVG_DURATION_BETWEEN_TWO_WGB,(avgDurationBetweenTwoWgb*(count_wgb-1) + differenceBetweenTwoTimes(time, lastTimeWgb))/count_wgb );
					}
					
					
					double duration = Double.parseDouble(incomingEdge.getAnnotation(DURATION));
					double currentDurationMean = destinationProcess.get(AVG_DURATION_WGB);
					destinationProcess.put(AVG_DURATION_WGB,(currentDurationMean*count_wgb + duration)/(count_wgb+1) );
					destinationProcess.put(COUNT_WGB, count_wgb + 1);
					
					if(sourceVertex.getAnnotation(CLASS) == FILE_SYSTEM){
						double count_filesystem = destinationProcess.get(COUNT_FILESYSTEM_WGB);
						destinationProcess.put(COUNT_FILESYSTEM_WGB, count_filesystem + 1);
					}
				}
			}
			
			
			
			putInNextFilter(incomingEdge);
		}else{
			logger.log(Level.WARNING, "Invalid edge: {0}, source: {1}, destination: {2}", new Object[]{
					incomingEdge,
					incomingEdge == null ? null : incomingEdge.getSourceVertex(),
					incomingEdge == null ? null : incomingEdge.getDestinationVertex()
			});
		}
	}
	 
	
	/*
	 * do t1 - t2 and send the result in 10 nanoseconds
	 * we suppose t1 > t2
	 */
	public static int differenceBetweenTwoTimesWOAmPm(String[] time1,String[] time2){

		int result = 0;
		int secondCarry = 0;
		int minuteCarry = 0;
		int hourCarry = 0;
		
		// time[0]  = hour, time[1] = minutes, time[2] = seconds, time[3] = 10 nanoseconds, time[4] = AM or PM
		
		int nanosec1 = Integer.parseInt(time1[3]);
		int nanosec2 = Integer.parseInt(time2[3]);
		if (nanosec1 >= nanosec2){
			result += nanosec1 - nanosec2; 
		}else{
			result += SCALE_TIME + nanosec1 - nanosec2;
			secondCarry += 1;
		}
			
		int second1 = Integer.parseInt(time1[2]);
		int second2 = Integer.parseInt(time2[2]);
			
		if ((second1-secondCarry) >= second2){
			result += (second1 - secondCarry - second2)*SCALE_TIME;
		}else{
			result += (60 + second1 - secondCarry - second2)*SCALE_TIME;
			minuteCarry += 1;
		}
			
		int minute1 = Integer.parseInt(time1[1]);
		int minute2 = Integer.parseInt(time2[1]);
			
		if((minute1 - minuteCarry) >= minute2){
			result += (minute1 - minuteCarry - minute2)*60*SCALE_TIME;
		}else{
			result += (60 + minute1 - minuteCarry - minute2)*60*SCALE_TIME;
			hourCarry += 1; 
		}
			
		int hour1 = Integer.parseInt(time1[0]);
		int hour2 = Integer.parseInt(time2[0]);
			
		if((hour1 - hourCarry) >= hour2){
			result += (hour1 - hourCarry - hour2)*60*60*SCALE_TIME;
		}else{
			result += (60 + hour1 - hourCarry - hour2)*60*60*SCALE_TIME;
		}
			
		
		return result;
	}
	
	/*
	 * do t1 - t2 and send the result in 10 nanoseconds
	 * we suppose t1 > t2
	 */
	public static double differenceBetweenTwoTimes(String t1,String t2){
		String[] time1 = t1.split(":|\\.| ");
		String[] time2 = t2.split(":|\\.| ");
		int result = 0;
		// time[0]  = hour, time[1] = minutes, time[2] = seconds, time[3] = 10 nanoseconds, time[4] = AM or PM
		if(!time1[4].equals(time2[4])){
			time1[0] =Integer.toString(Integer.parseInt(time1[0])+12);
		}
		result = differenceBetweenTwoTimesWOAmPm(time1, time2);
		
		return (double)result;
	}

	/*
	 * Length sometimes appears in the detail annotation with the form xxx: xxx, Length: 123,123, xxx: ...
	 */
	public static double getLengthFromDetailAnnotation(String detail){
		int result = 0;
		Pattern pattern = Pattern.compile("(Length:.*, )|(Length:.*)");
		Matcher matcher = pattern.matcher(detail);
		if(matcher.find()){
			String lengthPattern = matcher.group();
			lengthPattern = lengthPattern.substring(8);
			String[] digitDecomposition = lengthPattern.split(",");
			int n = digitDecomposition.length;
			if(digitDecomposition[n-1].equals(" ")){
				n -= 1;
			}
			int powerOfThousand = 1;
			for(int i = n-1; i >= 0; i--){
				result += powerOfThousand*Integer.parseInt(digitDecomposition[i]);
				powerOfThousand *= 1000;
			}
		}
		
		
		return (double)result;
	}
	
}
