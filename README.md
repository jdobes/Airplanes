# Airplanes

A simple Android flight tracker that shows aircraft near the user's location on an interactive MapLibre map, with a continuously-cycling detail panel (photo, route, airline, altitude) for each plane in range.

![Screenshot](Screenshot_20260628-181349_Airplanes.jpg)

## Data Sources

- [ADSB.fi open data](https://opendata.adsb.fi/) — live aircraft positions within a 9 NM radius.
- [ADS-B DB](https://api.adsbdb.com/) — aircraft owner country and flight route / airline metadata.
- [PlaneSpotters](https://www.planespotters.net/) — aircraft photos by registration.
- [OpenFreeMap](https://tiles.openfreemap.org/styles/liberty) — liberty map style (MapLibre SDK).

## Tech Stack

Jetpack Compose + Material3, MapLibre 13.3.1, Retrofit 2.11 + kotlinx.serialization, OkHttp 4.12, Coil 2.7, Play Services Location.
