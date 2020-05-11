package infore.SDE;


import java.util.ArrayList;
import java.util.List;

import infore.SDE.sources.kafkaProducerEstimation;
import org.apache.flink.api.common.JobExecutionResult;
import org.apache.flink.api.common.functions.MapFunction;
import org.apache.flink.api.java.functions.KeySelector;
import org.apache.flink.api.java.tuple.Tuple2;


import org.apache.flink.shaded.jackson2.com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.flink.streaming.api.collector.selector.OutputSelector;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.datastream.SplitStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;

import infore.SDE.transformations.ReduceFlatMap;
import infore.SDE.transformations.RqRouterFlatMap;
import infore.SDE.transformations.SDEcoFlatMap;
import infore.SDE.transformations.dataRouterCoFlatMap;
import infore.SDE.messages.Estimation;
import infore.SDE.messages.Request;
import infore.SDE.sources.kafkaConsumer;

/**
 * <br>
 * Implementation code for SDE for INFORE-PROJECT" <br> *
 * ATHENA Research and Innovation Center <br> *
 * Author: Antonis_Kontaxakis <br> *
 * email: adokontax15@gmail.com *
 */

@SuppressWarnings("deprecation")
public class Run {

	private static String kafkaDataInputTopic;
	private static String kafkaRequestInputTopic;
	private static String kafkaBrokersList;
	private static int parallelism;
	private static String kafkaOutputTopic;

	/**
	 * @param args Program arguments. You have to provide 4 arguments otherwise
	 *             DEFAULT values will be used.<br>
	 *             <ol>
	 *             <li>args[0]={@link #kafkaDataInputTopic} DEFAULT: "SpringI2")
	 *             <li>args[1]={@link #kafkaRequestInputTopic} DEFAULT: "rq13")
	 *             <li>args[2]={@link #kafkaBrokersList} (DEFAULT: "192.168.1.3:9092")
	 *             <li>args[3]={@link #parallelism} Job parallelism (DEFAULT: "6")
	 *             <li>args[4]={@link #kafkaOutputTopic} DEFAULT: "rq13")
	 *             "O10")
	 *             </ol>
	 * @throws Exception
	 */

	public static void main(String[] args) throws Exception {

		// Initialize Input Parameters
		initializeParameters(args);
		StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
		env.setParallelism(parallelism);

		kafkaConsumer kc = new kafkaConsumer(kafkaBrokersList, kafkaDataInputTopic);
		kafkaConsumer requests = new kafkaConsumer(kafkaBrokersList, kafkaRequestInputTopic);
		kafkaProducerEstimation kp = new kafkaProducerEstimation(kafkaBrokersList, kafkaOutputTopic);
		//kafkaProducerEstimation test = new kafkaProducerEstimation(kafkaBrokersList, "testPairs");
		
		DataStream<ObjectNode> datastream = env.addSource(kc.getFc());
		DataStream<ObjectNode> RQ_stream = env.addSource(requests.getFc());

		//map kafka data input to tuple2<int,double>
		DataStream<Tuple2<String, String>> dataStream = datastream
				.map(new MapFunction<ObjectNode, Tuple2<String, String>>() {
					/**
					 * 
					 */
					private static final long serialVersionUID = 1L;
				
					@Override
					public Tuple2<String, String> map(ObjectNode node) {
						// TODO Auto-generated method stub
						return new Tuple2<>(node.get("key").toString().replace("\"", ""), node.get("value").toString().replace("\"", ""));
				}
			}).keyBy((KeySelector<Tuple2<String, String>, String>) r -> r.f0);
		
		//DataStream<Tuple2<String, String>> dataStream = datastream.flatMap(new IngestionMultiplierFlatMap(multi)).setParallelism(parallelism2).keyBy(0);
		
		DataStream<Request> RQ_Stream = RQ_stream
				.map(new MapFunction<ObjectNode, Request>() {
					private static final long serialVersionUID = 1L;
					@Override
					public Request map(ObjectNode node) throws Exception {
						// TODO Auto-generated method stub
						String[] valueTokens = node.get("value").toString().replace("\"", "").split(",");
						if(valueTokens.length > 6) {
						return new Request(node.get("key").toString().replace("\"", ""),valueTokens);
						}
						return null;
					}
				}).keyBy((KeySelector<Request, String>) Request::getKey);
			
		DataStream<Request> SynopsisRequests = RQ_Stream
				.flatMap(new RqRouterFlatMap()).keyBy((KeySelector<Request, String>) Request::getKey);

		DataStream<Tuple2<String, String>> DataStream = dataStream.connect(RQ_Stream)
				.flatMap(new dataRouterCoFlatMap()).keyBy((KeySelector<Tuple2<String, String>, String>) r -> r.f0);
		//dataStream.print();
		DataStream<Estimation> estimationStream = DataStream.connect(SynopsisRequests)
				.flatMap(new SDEcoFlatMap());

		//estimationStream.writeAsText("cm", FileSystem.WriteMode.OVERWRITE);
       
		SplitStream<Estimation> split = estimationStream.split(new OutputSelector<Estimation>() {
			private static final long serialVersionUID = 1L;
			@Override
			public Iterable<String> select(Estimation value) {
				// TODO Auto-generated method stub
				 List<String> output = new ArrayList<>();
				 if (value.getNoOfP() == 1) {
			            output.add("single");
			        }
			        else {
			            output.add("multy");
			        }
			        return output;
				}
			});   
		
		DataStream<Estimation> single = split.select("single");
		DataStream<Estimation> multy = split.select("multy").keyBy((KeySelector<Estimation, String>) Estimation::getKey);
		//single.addSink(kp.getProducer());
		DataStream<Estimation> finalStream = multy.flatMap(new ReduceFlatMap()).keyBy((KeySelector<Estimation, String>) Estimation::getKey);
		//DataStream<Tuple2< String, Object>> finalStream = estimationStream.flatMap(new ReduceFlatMap());
		finalStream.addSink(kp.getProducer());

		JobExecutionResult result = env.execute("Streaming SDE");

}

	private static void initializeParameters(String[] args) {

		if (args.length > 4) {

			System.out.println("[INFO] User Defined program arguments");
			//User defined program arguments
			kafkaDataInputTopic = args[0];
			kafkaRequestInputTopic = args[1];
			kafkaOutputTopic = args[2];
			kafkaBrokersList = args[3];
			//kafkaBrokersList = "localhost:9092";
			parallelism = Integer.parseInt(args[4]);
			//parallelism2 = Integer.parseInt(args[5]);
			//multi = Integer.parseInt(args[5]);

		}else{
			
			System.out.println("[INFO] Default values");
			//Default values
			kafkaDataInputTopic = "Forex";
			kafkaRequestInputTopic = "Requests";
			parallelism = 4;
			//parallelism2 = 4;
			//kafkaBrokersList = "clu02.softnet.tuc.gr:6667,clu03.softnet.tuc.gr:6667,clu04.softnet.tuc.gr:6667,clu06.softnet.tuc.gr:6667";
			kafkaBrokersList = "localhost:9092";
			kafkaOutputTopic = "OUT";
			
		}
	}
}
