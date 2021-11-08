# PLANitOSM

PLANitOSM provides parsers that are able to parse Open Street Map data and convert these into a PLANit compatible networks. A large number of options is provided to manipulate and filter the OSM data to extract the network that the user desires. The resulting networks are topologically sound and particularly aimed at being useful for traffic assignment and simulation purposes, although they can also be used to just filter our particular aspects of the OSM data. The created networks can in turn be exported to other formats, or manipulated in memory. 

User documentation on how to use this reader can be found on the PLANit website: [www.goPLANit.org](http://www.goplanit.org).

Some technical documentation/introduction for developers regarding the design of the reader can be found in the [Technical Readme](./technical_readme.md)

More information on What Open Street Map is can be found on the [Open Street Map wiki](https://wiki.openstreetmap.org/wiki/Main_Page)

> Implementation of PLANitOSM is partially funded by the University of Sydney and the Australian Transport Research Cloud ([ATRC](https://ardc.edu.au/project/australian-transport-research-cloud-atrc/)). ATRC is a project instigated by the Australian Research Data Cloud ([ARDC](www.ardc.edu.au)).

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

## Development

### Maven parent

PLANit OSM has the following PLANit specific dependencies (See pom.xml):

* planit-parentpom
* planit-core
* planit-utils

Dependencies will be automatically downloaded from the PLANit website, (www.repository.goplanit.org)[http://repository.goplanit.org], or alternatively can be checked-out locally for local development. The shared PLANit Maven configuration can be found in planit-parent-pom which is defined as the parent pom of each PLANit repository.

> When developing on multiple PLANit projects locally, including the parent-pom; make sure you install the PLANitParentPom pom.xml before conducting a Maven build (in for example Eclipse), otherwise it resorts to the online repository rather then the local one.

### Maven deploy

Distribution management is setup via the parent pom such that Maven deploys this project to the PLANit online repository (also specified in the parent pom). To enable deployment ensure that you setup your credentials correctly in your settings.xml as otherwise the deployment will fail.

### Git Branching model

We adopt GitFlow as per https://nvie.com/posts/a-successful-git-branching-model/