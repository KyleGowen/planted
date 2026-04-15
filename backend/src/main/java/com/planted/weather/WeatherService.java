package com.planted.weather;

import java.util.Optional;

public interface WeatherService {

    Optional<WeatherSnapshot> fetchSnapshot(double latitude, double longitude);
}
