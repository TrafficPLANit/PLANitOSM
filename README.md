# PLANitOSM

PLANitOSM provides parsers that are able to parse Open Street Map data and convert these into a PLANit compatible networks. A large number of options is provided to manipulate and filter the OSM data to extract the network that the user desires. The resulting networks are topologically sound and particularly aimed at being useful for traffic assignment and simulatino purposes, although they can also be used to just filter our particular aspects of the OSM data. The created networks can in turn be exported to other formats, or manipulated in memory. 

User documentation on how to use this reader can be found on the PLANit website: [www.goPLANit.org](http://www.goplanit.org).

Some technical documentation/introduction for developers regarding the design of the reader can be found in the [Technical Readme](./technical_readme.md)

More information on What Open Street Map is can be found on the [Open Street Map wiki](https://wiki.openstreetmap.org/wiki/Main_Page)  

## OSM4j

This implementation uses OSM4J to access the OSM data and manipulate it.

From the OSM4J website:

```
An OSM dataset is basically a long list of nodes, ways and relations that is encoded using one of the basic storage file formats. osm4j provides access to data encoded in the most important of these data formats by providing OsmReader and OsmIterator implementations for them.

All basic data formats have in common, that they store their data in a specific order:
They contain a sequence of nodes, followed by a sequence of ways, followed by a sequence of relations.
Each sequence contains its elements ordered by the objects' ids in ascending order.

Thus, when processing an OpenStreetMap dataset, you will encounter the contained data in exactly this order. Also, when writing a dataset to some output using an OsmOutputStream it is important to feed the elements to the stream in the correct order.

It is important to understand that ways and relations reference other objects using their ids. They do not contain the data of referenced objects themselves. Hence, to work with a way or relation it is usually necessary to resolve those references and find the actual objects they reference.
For example, a way is just a sequence of node ids. To interpret the geometry of the way, you have to assemble a sequence of coordinates from the references by finding the referenced nodes by their id.
```
## Maven parent

Projects need to be built from Maven before they can be run. The common maven configuration can be found in the PLANitParentPom project which acts as the parent for this project's pom.xml.

> Make sure you install the PLANitParentPom pom.xml before conducting a maven build (in Eclipse) on this project, otherwise it cannot find the references dependencies, plugins, and other resources.

## Git Branching model

We adopt GitFlow as per https://nvie.com/posts/a-successful-git-branching-model/