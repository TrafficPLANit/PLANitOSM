# Release Log

PLANitOSM release log.

## 0.3.0

* edge segments of broken edges are not properly removed from vertices when updating them, leading to too many entry and exit edge segments. This has been fixed #10
* OSM access:<mode> tags are not yet parsed causing some links to not have support for modes included this way. this has been fixed #12
* parse stops, platforms and other PT related infrastructure and support them in PLANit memory model #8
* add support to remove dangling transfer zones in the end stage of parsing as well as recreating ids (to remain contiguous), because we parsed almost all transfer zones to avoid missing some that are incorrectly tagged #15
* modes on parsed transfer zones are now cross-referenced with stop_positions/connectoids to reduce the matches in case modes are incompatible #14
* creation of connectoids for transfer zones on roads now map only to one side of the road (edge segment), unless specific stop_positions are referenced on multiple nodes #13
* be able to specify smaller bounding box to only parse parts of an OSM file #19
* mode access by highway type (road type) configuration for specific countries/applications now parsable from file #4
* default speed limits globally and per country now parsed from resource files #1
* added support for parsing OSM streams rather than files #24
* updated artifact id to conform with how this generally is setup, i.e. <application>-<subrepo> #25
* update packages to conform to new domain org.goplanit.* #27
* permissibe ways should not obtain all available modes as allowed, but only the modes supported by the way type (and layer). This has been fixed #26

## 0.2.0

* first implementation to parse OSM network from xml/pbf files (no public transport routes/services/stops)
* add LICENSE.TXT to each repository so it is clearly licensed (planit/#33)
* be able to process access restrictions and additional mode specific tags on ways that highlight mode access (planitosm/#6) 

