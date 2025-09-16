This readme will assume you have maven installed, but if you don't, this project has a maven wrapper. You should be able to replace all ```mvn``` commands with ```mvnw```

This is my submission for the Distributed Systems Assignment 2. I made some compromises on the implementation for lack of time. Namely, I did not make the Aggregation Server crash-tolerant, so it does not back data up to a file but instead holds it in an array. Further, the system doesn't discard updates from servers that haven't been contacted in over 30 seconds, but only maintains freshness by keeping the most recent 20 updates by lamport clock time. Any other relevant design notes can be found in the attached design file.

In order to compile the project code and run the test cases, you need only navigate your terminal to the project directory and use the command ```mvn test```. The test cases will take about 30 seconds to complete on average (Thread.sleep() is the main culprit). If you are interested in what the tests are looking for, you can read the tests' source code in the src/test/java/ directory. The code has comments explaining what the test cases are looking for.

If you wish to compile without running the test cases, you can use the command ```mvn compile```. An IDE like IntelliJ should also be able to compile and run the project. If you want to see the system running for an indefinite period, with any number of GETClients and ContentServers, you can manually run the three classes AggregationServer, ContentServer and GETClient in that order.

Unfortunately, I could not think of a test case to demonstrate the AggregationServer handling multiple clients, but if you are willing to manually run the classes, you can demonstrate for yourself that it works.

The implementation uses Gson to handle JSON parsing.