# SDA Commons Server Morphia Example

This module is an example how to use the [morphia bundle](../sda-commons-server-morphia/README.md).

The application creates a connection to a mongo database and stores [`cars`](./src/main/java/org/sdase/commons/server/morphia/example/mongo/model/Car.java) into a collection.
The [`CarsManager`](./src/main/java/org/sdase/commons/server/morphia/example/mongo/CarManager.java) encapsulates the access methods to the datastore.

The example also shows how the datastore object (created within the bundle) can be used in other classes by dependency injection (WELD).

The demonstration integration test [`MorphiaApplicationIT`](./src/test/java/org/sdase/commons/server/morphia/example/MorphiaApplicationIT.java) shows
how to use the `MongoDBRule` to create a WELD capable Dropwizard application that uses a mongo database in a test case. 

Note: The application is not meant to be started. It's only used for integration test purposes.
