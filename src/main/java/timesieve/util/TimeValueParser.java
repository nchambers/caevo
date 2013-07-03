package timesieve.util;

import java.util.Calendar;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 
 * Parses out normalized TimeML times into ranges of dates.  
 * 
 * Dates based on weeks are currently only roughly estimated, although it's possible
 * to represent them precisely.
 * 
 * @author Bill McDowell
 * 
 */
public class TimeValueParser {
	private class TimePattern {
		private Pattern pattern;
		
		private int yearGroup;
		private int monthGroup;
		private int dayGroup;
		private int hourGroup;
		private int minuteGroup;
		private int secondGroup;
		private int yearPartGroup;
		private int seasonGroup;
		private int weekGroup;
		private int timeOfDayGroup;
		private int weekDayGroup;
		
		public TimePattern(String pattern, 
											 int yearGroup, 
											 int monthGroup, 
											 int dayGroup,
											 int hourGroup,
											 int minuteGroup,
											 int secondGroup,
											 int yearPartGroup,
											 int seasonGroup,
											 int weekGroup,
											 int timeOfDayGroup,
											 int weekDayGroup) {
			this.pattern = Pattern.compile(pattern);
			this.yearGroup = yearGroup; 
			this.monthGroup = monthGroup;
			this.dayGroup = dayGroup;
			this.hourGroup = hourGroup;
			this.minuteGroup = minuteGroup;
			this.secondGroup = secondGroup;
			this.yearPartGroup = yearPartGroup;
			this.seasonGroup = seasonGroup;
			this.weekGroup = weekGroup;
			this.timeOfDayGroup = timeOfDayGroup;
			this.weekDayGroup = weekDayGroup;
		}
		
		public int yearGroup() { return this.yearGroup; }
		public int monthGroup() { return this.monthGroup; }
		public int dayGroup() { return this.dayGroup; }
		public int hourGroup() { return this.hourGroup; }
		public int minuteGroup() { return this.minuteGroup; }
		public int secondGroup() { return this.secondGroup; }
		public int yearPartGroup() { return this.yearPartGroup; }
		public int seasonGroup() { return this.seasonGroup; }
		public int weekGroup() { return this.weekGroup; }
		public int timeOfDayGroup() { return this.timeOfDayGroup; }
		public int weekDayGroup() { return this.weekDayGroup; }
		
		public String getPatternString() {
			return this.pattern.toString();
		}
		
		public Matcher getMatcher(String text) {
			return this.pattern.matcher(text);
		}
		
	}
	
  private TimePattern DATE_PATTERN = new TimePattern(
  		"([0-9X]{1,4})(-([0-9X]{1,2})(-([0-9X]{1,2}))?)?",
  				1,3,5,0,0,0,0,0,0,0,0);
  private TimePattern TIME_PATTERN = new TimePattern(
  		"(([0-9X]{1,4})-([0-9X]{1,2})-([0-9X]{1,2}))?T((([0-9]{2})(:([0-9]{2})(:([0-9]{2}))?)?)|(MO|MI|AF|EV|NI|DT))",
  				2,3,4,7,9,11,0,0,0,12,0);
  private TimePattern WEEK_DATE_PATTERN = new TimePattern(
  		"([0-9X]{1,4})-W([0-9X]{1,2})(-([1-7X]|WE))?",
  				1,0,0,0,0,0,0,0,2,0,4);
  private TimePattern WEEK_TIME_PATTERN = new TimePattern(
  		"([0-9X]{1,4})-W([0-9X]{1,2})-([1-7X])T((([0-9]{2})(:([0-9]{2})(:([0-9]{2}))?)?)|(MO|MI|AF|EV|NI|DT))",
  				1,0,0,6,8,10,0,0,2,11,3);
  private TimePattern SEASON_PATTERN = new TimePattern(
  		"([0-9X]{1,4})-(SP|SU|WI|FA)",
  				1,0,0,0,0,0,0,2,0,0,0);
  private TimePattern PART_OF_YEAR_PATTERN = new TimePattern(
  		"([0-9X]{1,4})-(H[1-2X]|Q[1-4X])",
  				1,0,0,0,0,0,2,0,0,0,0);
	
  private String value;
  private TimePattern pattern; 
  private Matcher matcher;
  
	public TimeValueParser(String value) {
		this.value = value;
	
		Matcher dateMatcher = this.DATE_PATTERN.getMatcher(this.value);
		Matcher timeMatcher = this.TIME_PATTERN.getMatcher(this.value);
		Matcher weekDateMatcher = this.WEEK_DATE_PATTERN.getMatcher(this.value);
		Matcher weekTimeMatcher = this.WEEK_TIME_PATTERN.getMatcher(this.value);
		Matcher seasonMatcher = this.SEASON_PATTERN.getMatcher(this.value);
		Matcher partOfYearMatcher = this.PART_OF_YEAR_PATTERN.getMatcher(this.value);
		
		if (dateMatcher.matches()) {
			this.pattern = this.DATE_PATTERN;
			this.matcher = dateMatcher;			
		} else if (timeMatcher.matches()) {
			this.pattern = this.TIME_PATTERN;
			this.matcher = timeMatcher;				
		} else if (weekDateMatcher.matches()) {
			this.pattern = this.WEEK_DATE_PATTERN;
			this.matcher = weekDateMatcher;	
		} else if (weekTimeMatcher.matches()) {
			this.pattern = this.WEEK_TIME_PATTERN;
			this.matcher = weekTimeMatcher;	
		} else if (seasonMatcher.matches()) {
			this.pattern = this.SEASON_PATTERN;
			this.matcher = seasonMatcher;	
		} else if (partOfYearMatcher.matches()) {
			this.pattern = this.PART_OF_YEAR_PATTERN;
			this.matcher = partOfYearMatcher;	
		}
		
	}
	
	public Pair<Integer, Integer> getYears() { 
		if (this.pattern == null || this.pattern.yearGroup() == 0)
			return null;

		String yearStr = this.matcher.group(this.pattern.yearGroup());
		if (!isInteger(yearStr))
			return null;
		
		StringBuilder minYear = new StringBuilder().append(yearStr);
		StringBuilder maxYear = new StringBuilder().append(yearStr);
		
		for (int i = yearStr.length(); i < 4; i++) {
			minYear.append("0");
			maxYear.append("9");
		}
		
		return new Pair<Integer,Integer>(Integer.valueOf(minYear.toString()), Integer.valueOf(maxYear.toString()));
	}
	
	public Pair<Integer, Integer> getMonths() { 
		if (this.pattern == null)
			return null;
		
		if (this.pattern.monthGroup() != 0) {
			Integer month = extractMatcherInteger(this.pattern.monthGroup());
			if (month != null)
				return new Pair<Integer, Integer>(month-1, month-1);
		} else if (this.pattern.weekGroup() != 0) {
			Integer week = extractMatcherInteger(this.pattern.weekGroup());
			if (week != null)
				return new Pair<Integer, Integer>(weekToMonth(week)-1, weekToMonth(week)-1);
		} else if (this.pattern.seasonGroup() != 0) {
			String season = this.matcher.group(this.pattern.seasonGroup());
			if (season.equals("SP")) {
				return new Pair<Integer, Integer>(2, 5);
			} else if (season.equals("SU")) {
				return new Pair<Integer, Integer>(5, 8);			
			} else if (season.equals("FA")) {
				return new Pair<Integer, Integer>(8, 11);			
			} else if (season.equals("WI")) {
				return new Pair<Integer, Integer>(11, 2);			
			}
		} else if (this.pattern.yearPartGroup() != 0) {
			String yearPart = this.matcher.group(this.pattern.yearPartGroup());
			if (yearPart.equals("H1")) {
				return new Pair<Integer, Integer>(0, 5);
			} else if (yearPart.equals("H2")) {
				return new Pair<Integer, Integer>(6, 11);
			} else if (yearPart.equals("Q1")) {
				return new Pair<Integer, Integer>(0, 2);
			} else if (yearPart.equals("Q2")) {
				return new Pair<Integer, Integer>(3, 5);				
			} else if (yearPart.equals("Q3")) {
				return new Pair<Integer, Integer>(6, 8);			
			} else if (yearPart.equals("Q4")) {
				return new Pair<Integer, Integer>(9, 11);			
			}
		}
		
		return null;
	}
	
	public Pair<Integer, Integer> getDays() { 
		/* FIXME: Add support to find day based on week and week day */
		if (this.pattern == null)
			return null;
		
		if (this.pattern.dayGroup() != 0) {
			Integer day = extractMatcherInteger(this.pattern.dayGroup());
			if (day != null)
				return new Pair<Integer, Integer>(day, day);
		} else if (this.pattern.seasonGroup() != 0) {
			String season = this.matcher.group(this.pattern.seasonGroup());
			if (season.equals("SP")) {
				return new Pair<Integer, Integer>(20,20); /* March 20 - June 20 */
			} else if (season.equals("SU")) {
				return new Pair<Integer, Integer>(21,21); /* June 21 - September 21 */
			} else if (season.equals("FA")) {
				return new Pair<Integer, Integer>(22,20); /* September 22 - December 20 */			
			} else if (season.equals("WI")) {
				return new Pair<Integer, Integer>(21,19); /* December 21 - March 19 */			
			}
		}
		
		return null;
	}

	public Pair<Integer, Integer> getHours() {
		if (this.pattern == null)
			return null;
		
		if (this.pattern.hourGroup() != 0) {
			Integer hour = extractMatcherInteger(this.pattern.hourGroup());
			if (hour != null)
				return new Pair<Integer, Integer>(hour, hour);
		} 
		
		if (this.pattern.timeOfDayGroup() != 0) {
			String timeOfDay = this.matcher.group(this.pattern.timeOfDayGroup());
			if (timeOfDay.equals("MO")) {
				return new Pair<Integer, Integer>(4,7);
			} else if (timeOfDay.equals("MI")) {
				return new Pair<Integer, Integer>(8,11);				
			} else if (timeOfDay.equals("AF")) {
				return new Pair<Integer, Integer>(12,15);				
			} else if (timeOfDay.equals("EV")) {
				return new Pair<Integer, Integer>(16,19);				
			} else if (timeOfDay.equals("NI")) {
				return new Pair<Integer, Integer>(20,23);				
			} else if (timeOfDay.equals("DT")) {
				return new Pair<Integer, Integer>(0,3);
			}
		}
		
		return null;
	}
	
	public Pair<Integer, Integer> getMinutes() {
		if (this.pattern == null)
			return null;
		
		if (this.pattern.minuteGroup() != 0) {
			Integer minute = extractMatcherInteger(this.pattern.minuteGroup());
			if (minute != null)
				return new Pair<Integer, Integer>(minute, minute);
		}
		
		return null;
	}
	
	public Pair<Integer, Integer> getSeconds() { 
		if (this.pattern == null)
			return null;
		
		if (this.pattern.secondGroup() != 0) {
			Integer second = extractMatcherInteger(this.pattern.secondGroup());
			if (second != null)
				return new Pair<Integer, Integer>(second, second);
		}
		
		return null;
	}
	
	public Pair<Calendar, Calendar> getRange() {

		Pair<Integer, Integer> years = getYears();
		Pair<Integer, Integer> months = getMonths();
		Pair<Integer, Integer> days = getDays();
		Pair<Integer, Integer> hours = getHours();
		Pair<Integer, Integer> minutes = getMinutes();
		Pair<Integer, Integer> seconds = getSeconds();
		

		/* DEBUG: System.out.println("Value: " + this.value + " Years: " + years + 
				" Months: " + months + 
				" Days: " + days + 
				" Hours: " + hours + 
				" Minutes: " + minutes +
				" Seconds: " + seconds +
				" Pattern: " + this.pattern.getPatternString());*/
		
		if (years == null) {
			return null;
		}
		
		Calendar minTime = Calendar.getInstance();
		minTime.set(Calendar.MILLISECOND, 0);
		minTime.set(
						years.first(), 
						months == null ? 1 : months.first(), 
						days == null ? 1 : days.first(), 
						hours == null ? 0 : hours.first(), 
						minutes == null ? 0 : minutes.first(), 
						seconds == null ? 0 : seconds.first());
		
		Calendar maxTime = Calendar.getInstance();
		maxTime.set(Calendar.MILLISECOND, 0);
		maxTime.set(
				years.second(), 
				months == null ? maxTime.getLeastMaximum(Calendar.MONTH) : months.second(), 
				days == null ?  maxTime.getLeastMaximum(Calendar.DAY_OF_MONTH) : days.second(), 
				hours == null ?  maxTime.getLeastMaximum(Calendar.HOUR_OF_DAY) : hours.second(), 
				minutes == null ?  maxTime.getLeastMaximum(Calendar.MINUTE) : minutes.second(), 
				seconds == null ?  maxTime.getLeastMaximum(Calendar.SECOND) : seconds.second());

		return new Pair<Calendar, Calendar>(minTime, maxTime);
	}
	
	private int weekToMonth(int week) {
		return (int)Math.ceil(Math.min(12, week/4.33)); // FIXME: This is just a rough estimate
	}
	
	private Integer extractMatcherInteger(int group) {
		String str = this.matcher.group(group);
		if (str == null || !isInteger(str))
			return null;
		else
			return Integer.valueOf(str);
	}
	
	private boolean isInteger(String str) {
		for (int i = 0; i < str.length(); i++)
			if (!Character.isDigit(str.charAt(i)))
				return false;
		return true;
	}
}
