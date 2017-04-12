# ChatServer
A chat server made with Scala and Akka

## Data Processing
A real time reactive, back pressured data stream processing pipeline for transforming data as it is ingested. This kind of stream can be used in congestion with Apache kafka or real time predictions on the data. So for this particular pipeline I used StackOverFlow data dump 
hosted at https://archive.org/details/stackexchange it is compatible with all the datasets available there. So you can download any dataset from there and can run this pipeline. A flow graph is provided in the source code for understanding how this transformation happens.

# Flow Graph
![Alt text](FlowGraph.jpg?raw=true "Optional Title")

# A simple iot use of akka streams using RabbitMQ
![Alt text](simpleIot.png?raw=true "Optional Title")
