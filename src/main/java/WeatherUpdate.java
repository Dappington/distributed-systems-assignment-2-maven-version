//package com.distsystems.assignment2.common;

public class WeatherUpdate {
//    This object will contain the update that can be stringified and sent to the aggregation server. This will break if the update is not of a very specific format, but the assignment description said I could assume it would be.
    public String id;
    public String name;
    public String state;
    public String time_zone;
    public double lat;
    public double lon;
    public String local_date_time;
    public String local_date_time_full;
    public double air_temp;
    public double apparent_t;
    public String cloud;
    public double dewpt;
    public double press;
    public double rel_hum;
    public String wind_dir;
    public double wind_spd_kmh;
    public double wind_spd_kt;
}
