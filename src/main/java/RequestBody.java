//package com.distsystems.assignment2.common;

import java.util.ArrayList;

public class RequestBody {
    public int req_lamport_timestamp;
    public WeatherUpdate weatherUpdate;
    public ArrayList<WeatherUpdate> weather_updates_list;
}