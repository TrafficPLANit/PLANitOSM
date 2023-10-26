# Release Log

PLANitOSM release log.

## 0.4.0

**Enhancements**

* [GENERAL] Support for waterways (ferries) in OSM parser
* [GENERAL] More fine grained options in OSM settings to configure network, zone parsing
* [GENERAL] Improved pipeline for identifying best mapping of stop to nearby OSM way
* #52, #51 Allow to connect dangling ferry stops to road network (for provided OSM modes) (default off)
* #47 Add option to suppress warnings for OSM stop area relations
* #44,43 Update to Junit5
* #41 Bring over improved way of identifying most appropriate access link for a waiting area (stop) from PLANitGTFS
* #39 GTFS support - STEP 5 - add unit tests for integrating GTFS and OSM for various Australian states
* #34 Add CI by running tests whenever pusing a commit
* #32 Reduce memory footprint required by supporting pre-parsing identifying and loading only the nodes that we should parse

**Bug fixes**

* #48 Defaults regarding modeaccess, speed limits etc, should be extended to use combined key/values, rather than only OSMway value as this is not unique
* #42 Should always construct PLANit entities in same sorted order. Needed to compare integration test results
* #31 when lanes value is not a number an exception was thrown and parser crashes. Instead default number of lanes should be used and issue should be logged
* #30 When maxspeed tag value is invalid, an exception is thrown and the link is dismissed rather than reverting to the default speed
* #29 When collecting mode support with post/prefix a nullpointerexception can occur as the tags are collected without the post-prefix in some cases

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

