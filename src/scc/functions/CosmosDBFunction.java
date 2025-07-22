package scc.functions;

import com.azure.cosmos.*;
import com.azure.cosmos.models.CosmosItemRequestOptions;
import com.azure.cosmos.models.CosmosQueryRequestOptions;
import com.azure.cosmos.models.PartitionKey;
import com.azure.cosmos.util.CosmosPagedIterable;
import com.microsoft.azure.functions.*;
import com.microsoft.azure.functions.annotation.*;
import redis.clients.jedis.Jedis;
import scc.cache.RedisCache;
import scc.data.QuestionDAO;
import scc.db.CosmosDBLayerUsers;
import scc.srv.UserResource;


import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Calendar;

import java.util.List;
import java.util.logging.Logger;

/**
 * Azure Functions with Timer Trigger.
 */
public class CosmosDBFunction {
	private static final Logger LOG = Logger.getLogger(CosmosDBFunction.class.getName());

	/** Azure information **/
	private static final String CONNECTION_URL = System.getenv("CONNECTION_URL");
	private static final String DB_KEY = System.getenv("DB_KEY");
	private static final String DB_NAME = System.getenv("DB_NAME");


	@FunctionName("cosmosHouseInsert")
	public void recentlyAddedHouses(@CosmosDBTrigger(name = "cosmosHouseInsert",
			databaseName = "scc2324",
			collectionName = "houses",
			preferredLocations = "West Europe",
			createLeaseCollectionIfNotExists = true,
			connectionStringSetting = "AzureCosmosDBConnection")
									String[] houses,
									final ExecutionContext context) {
		try (Jedis jedis = RedisCache.getCachePool().getResource()) {
			for (String h : houses) {
				jedis.lpush("recentlyAddedHouses", h);
			}
			jedis.ltrim("recentlyAddedHouses", 0, 9);
		}
	}

	@FunctionName("cosmosUserInsert")
	public void recentlyAddedUsers(@CosmosDBTrigger(name = "cosmosUserInsert",
			databaseName = "scc2324",
			collectionName = "users",
			preferredLocations = "West Europe",
			createLeaseCollectionIfNotExists = true,
			connectionStringSetting = "AzureCosmosDBConnection")
									   String[] users,
								   final ExecutionContext context) {
		try (Jedis jedis = RedisCache.getCachePool().getResource()) {
			LOG.warning("INSIDE recentlyAddedUsers FUNCTION");
			long cnt = 0;
			for (String u : users) {
				cnt = jedis.lpush("recentlyAddedUsers", u);
			}
			if (cnt > 10)
				jedis.ltrim("recentlyAddedUsers", 0, 9);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	@FunctionName("periodic-question-cleaner")
	public void cosmosFunction(@TimerTrigger(name = "periodicCleanOldQuestions",
			schedule = "0 59 23 * * *")
								   String timerInfo, ExecutionContext context) {

		CosmosClientBuilder clientBuilder = new CosmosClientBuilder()
				.endpoint(CONNECTION_URL)
				.key(DB_KEY);

		try (CosmosClient cosmosClient = clientBuilder.buildClient()) {

			CosmosDatabase db = cosmosClient.getDatabase(DB_NAME);

			CosmosContainer questions = db.getContainer("questions");

			Date currentDate = new Date();

			Calendar calendar = Calendar.getInstance();
			calendar.setTime(currentDate);
			calendar.add(Calendar.YEAR, -1);
			Date oneYearAgo = calendar.getTime();

			CosmosPagedIterable<QuestionDAO> results = questions.queryItems("SELECT * FROM questions WHERE questions.reply = \"\" AND questions.creationTime < " + oneYearAgo.getTime(),
					new CosmosQueryRequestOptions(), QuestionDAO.class);

			PartitionKey key;
			String questionId;

			try(Jedis jedis = RedisCache.getCachePool().getResource()){
				for(QuestionDAO questionDAO : results){
					questionId = questionDAO.getId();
					key = new PartitionKey(questionId);
					questions.deleteItem(questionId, key, new CosmosItemRequestOptions());
					jedis.del(questionId);
				}
			}catch(Exception e){
				LOG.severe("Something went wrong: "+e.getMessage());
			}
		}
	}

}
