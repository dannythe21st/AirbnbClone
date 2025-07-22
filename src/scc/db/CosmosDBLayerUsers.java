package scc.db;
import com.azure.cosmos.*;
import com.azure.cosmos.models.CosmosItemRequestOptions;
import com.azure.cosmos.models.CosmosItemResponse;
import com.azure.cosmos.models.CosmosQueryRequestOptions;
import com.azure.cosmos.models.PartitionKey;
import com.azure.cosmos.util.CosmosPagedIterable;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.ws.rs.core.NewCookie;
import jakarta.ws.rs.core.Response;
import redis.clients.jedis.Jedis;
import scc.cache.RedisCache;
import scc.data.*;
import org.apache.commons.codec.digest.DigestUtils;
import scc.utils.Session;
import java.util.*;
import java.util.logging.Logger;

public class CosmosDBLayerUsers {

	/** cache tags **/
	private static final String CACHE_USER_TAG = "user:";
	private static final String CACHE_HOUSE_TAG = "houses:";
	private static final String CACHE_RENTAL_TAG = "rentals:";
	private static final String CACHE_QUESTIONS_TAG = "questions:";

	/** Constants **/
	private static final String DELETED_USER_PREFIX = "DELETED_USER_";

	/** error messages **/
	private static final String SOMETHING_WENT_WRONG = "Something went wrong";

		/** user **/
	private static final String WRONG_PASSWORD = "Wrong password";
	private static final String INVALID_PASSWORD = "Invalid password";
	private static final String PASSWORDS_DONT_MATCH = "Passwords dont match";
	private static final String PASSWORD_SUCCESS = "Password changed with success!!";
	private static final String USER_NOT_FOUND = "User not found";
	private static final String USER_CREATED_SUCCESS = "User created successfully";
	private static final String USER_DELETED_SUCCESS = "User deleted successfully";
	private static final String USER_DELETED_SUCCESS_INCONSISTENT = "User deleted successfully, but information isn't consistent across the system";
	private static final String PROBLEM_OCCURRED_CREATE_USER = "A problem occurred when trying to create a user";
	private static final String PROBLEM_OCCURRED_DELETE_USER = "A Problem occurred while deleting a user";
	private static final String PROBLEM_OCCURRED_UPDATE_USER = "Couldn't update user";



	/** Azure information **/

	private static final String CONNECTION_URL = System.getenv("CONNECTION_URL");
	private static final String DB_KEY = System.getenv("DB_KEY");
	private static final String DB_NAME = System.getenv("DB_NAME");

	/** Container names**/
	private static final String USERS = "users";
	private static final String HOUSES = "houses";
	private static final String RENTALS = "rentals";
	private static final String QUESTIONS = "questions";

	/** TTL **/

	private static final int TTL_CACHE = 3600;


	private static CosmosDBLayerUsers instance;

	public static synchronized CosmosDBLayerUsers getInstance() {
		if( instance != null)
			return instance;
		LOG.warning("getting Instance");
		CosmosClient client = new CosmosClientBuilder()
		         .endpoint(CONNECTION_URL)
		         .key(DB_KEY)
		         //.directMode()
		         .gatewayMode()
		         // replace by .directMode() for better performance
		         .consistencyLevel(ConsistencyLevel.SESSION)
		         .connectionSharingAcrossClientsEnabled(true)
		         .contentResponseOnWriteEnabled(true)
		         .buildClient();
		instance = new CosmosDBLayerUsers( client);
		return instance;
	}

	private CosmosClient client;
	private CosmosDatabase db;
	private CosmosContainer users;
	private CosmosContainer houses;
	private CosmosContainer questions;
	private CosmosContainer rentals;

	private static final Logger LOG = Logger.getLogger(CosmosDBLayerUsers.class.getName());



	public CosmosDBLayerUsers(CosmosClient client) {
		this.client = client;

	}

	private synchronized void init(String containerName) {
		if( db == null)
			db = client.getDatabase(DB_NAME);
		switch(containerName){
			case USERS:
				this.users = db.getContainer(USERS);
			case HOUSES:
				this.houses = db.getContainer(HOUSES);
				break;
			case QUESTIONS:
				this.questions = db.getContainer(QUESTIONS);
				break;
			case RENTALS:
				this.rentals = db.getContainer(RENTALS);
				break;
			default:
		}
	}

	/** USERS **/

	public Response delUserById(String id) {
		init(USERS);

		PartitionKey key = new PartitionKey(id);
		try(Jedis jedis = RedisCache.getCachePool().getResource()) {
			jedis.del(CACHE_USER_TAG+id);

			CosmosItemResponse<Object> res = users.deleteItem(id, key, new CosmosItemRequestOptions());

			if(deleteFutureRentals(id) && setRenterAsDeleted(id) && setAskerAsDeleted(id) && deleteAllUserHouses(id)){
				return Response.status(res.getStatusCode()).entity(USER_DELETED_SUCCESS).build();
			}
			else{
				return Response.status(res.getStatusCode()).entity(USER_DELETED_SUCCESS_INCONSISTENT).build();
			}

		} catch (Exception e) {
			LOG.severe(e.getMessage());
			return Response.status(400).entity(PROBLEM_OCCURRED_DELETE_USER).build();
		}
	}

	/** delUserById auxiliary methods **/

	/**
	 * Delete future rentals the delete user had booked
	 */
	private boolean deleteFutureRentals(String userId){
		init(RENTALS);
		LOG.severe("deleteFutureRentals");
		Date date = new Date();
		CosmosPagedIterable<RentalDAO> results = rentals.queryItems("SELECT * FROM rentals WHERE rentals.renterId=\"" + userId +
				"\" AND rentals.inicial_date >= " + date.getTime(), new CosmosQueryRequestOptions(), RentalDAO.class);

		try(Jedis jedis = RedisCache.getCachePool().getResource()) {

			List<RentalDAO> rentalsToDelete = CosmosPagedIterableToArrayRentalDAO(results);
			PartitionKey key;
			for(RentalDAO rental : rentalsToDelete){
				LOG.warning("Rental: "+rental);
				String rentalID = rental.getId();
				key = new PartitionKey(rentalID);
				rentals.deleteItem(rentalID, key, new CosmosItemRequestOptions());
				jedis.del(rentalID);
			}
		}catch (Exception e){
			LOG.severe(e.getMessage());
			LOG.severe("deleteFutureRentals: Something went wrong while deleting future rentals");
			return false;
		}
		return true;
	}

	/**
	 * Set deleted user as such in previous rentals as a renter and house as deleted
	 */
	private boolean setRenterAsDeleted(String userId){
		init(RENTALS);

		LOG.severe("setRenterAsDeleted");

		Date date = new Date();
		CosmosPagedIterable<RentalDAO> results = rentals.queryItems("SELECT * FROM rentals WHERE rentals.renterId=\"" + userId
				+ "\" AND rentals.final_date < " + date.getTime(), new CosmosQueryRequestOptions(), RentalDAO.class);

		List<RentalDAO> rentalsToUpdate = CosmosPagedIterableToArrayRentalDAO(results);

		String deletedUserId = DELETED_USER_PREFIX+userId;
		ObjectMapper mapper = new ObjectMapper();

		for(RentalDAO rent : rentalsToUpdate){
			String rentalId = rent.getId();

			rent.setRenterId(deletedUserId);

			PartitionKey key = new PartitionKey(rentalId);

			rentals.replaceItem(rent, rentalId, key, new CosmosItemRequestOptions());
			try(Jedis jedis = RedisCache.getCachePool().getResource()){
				jedis.del(CACHE_RENTAL_TAG + rentalId);
				String JedisKey = CACHE_RENTAL_TAG + rentalId;
				jedis.set(JedisKey, mapper.writeValueAsString(rent));
				jedis.expire(JedisKey, TTL_CACHE);

			}
			catch (Exception e){
				LOG.severe(e.getMessage());
				LOG.severe("setRenterAsDeleted: Something went wrong while updating rents");
				return false;
			}
		}
		return true;
	}

	/**
	 * Set deleted user as such in a question as an asker
	 */
	private boolean setAskerAsDeleted(String userId){

		LOG.severe("setAskerAsDeleted");
		init(QUESTIONS);

		CosmosPagedIterable<QuestionDAO> results = questions.queryItems("SELECT * FROM questions WHERE questions.askerId=\"" + userId +"\"",
				new CosmosQueryRequestOptions(), QuestionDAO.class);

		List<QuestionDAO> questionList = CosmosPagedIterableToArrayQuestionDAO(results);
		PartitionKey key;
		ObjectMapper mapper = new ObjectMapper();
		String deletedUserID = DELETED_USER_PREFIX + userId;

		for(QuestionDAO question: questionList){

			String questionId = question.getId();

			question.setAskerId(deletedUserID);

			key = new PartitionKey(questionId);


			try(Jedis jedis = RedisCache.getCachePool().getResource()){
				questions.replaceItem(question, questionId, key, new CosmosItemRequestOptions());
				jedis.del(questionId);
				String JedisKey = CACHE_QUESTIONS_TAG+questionId;
				jedis.set(JedisKey, mapper.writeValueAsString(question));
				jedis.expire(JedisKey, TTL_CACHE);
			}catch (Exception e){
				LOG.severe(e.getMessage());
				LOG.severe("setAskerAsDeleted: Something went wrong while updating questions");
				return false;
			}
		}
		return true;
	}

	/**
	 * Deletes all future rentals for every house the deleted user owns
	 * Flags houses as inactive after that
	 */
	private boolean deleteAllUserHouses(String userId){
		LOG.severe("deleteAllUserHouses");
		init(HOUSES);

		CosmosPagedIterable <HouseDAO> results = houses.queryItems("SELECT * FROM houses WHERE houses.ownerId = \"" + userId+"\"",
				new CosmosQueryRequestOptions(), HouseDAO.class);

		//casas do user apagado
		List<HouseDAO> housesList = CosmosPagedIterableToArrayHouseDAOToHouseDAO(results);

		init(RENTALS);

		CosmosPagedIterable<RentalDAO> rentalResultFuture;
		Date date = new Date();

		for(HouseDAO house : housesList){
			// rentals futuros para cada casa do user apagado
			rentalResultFuture = rentals.queryItems("SELECT * FROM rentals WHERE rentals.houseId = \"" + house.getId() +
							"\" AND rentals.inicial_date >= " + date.getTime(), new CosmosQueryRequestOptions(), RentalDAO.class);

			List<RentalDAO> rentalsListFuture = CosmosPagedIterableToArrayRentalDAO(rentalResultFuture);

			if ( !deleteFutureRentalsForHouse(rentalsListFuture) || !flagHouseAsInactive(house))
				return false;
		}
		return true;
	}

	private boolean deleteFutureRentalsForHouse(List<RentalDAO> rentalsList){
		LOG.severe("deleteFutureRentals");

		if(!rentalsList.isEmpty()){
			init(RENTALS);
			// apagar rentals futuros
			PartitionKey key;
			for(RentalDAO rental : rentalsList){
				key = new PartitionKey(rental.getId());
				try(Jedis jedis = RedisCache.getCachePool().getResource()){
					rentals.deleteItem(rental.getId(),key, new CosmosItemRequestOptions());
					jedis.del(rental.getId());
				}catch(Exception e){
					LOG.severe(e.getMessage());
					LOG.severe("(1) deleteFutureRentals: Something went wrong while deleting a future rental");
					return false;
				}
			}
			return true;
		}
		return true;

	}

	private boolean flagHouseAsInactive(HouseDAO house){
		init(HOUSES);
		LOG.severe("flagHouseAsInactive");
		// Flag casa inativa
		PartitionKey key = new PartitionKey(house.getId());
		ObjectMapper mapper = new ObjectMapper();
		house.setActive(false);
		try(Jedis jedis = RedisCache.getCachePool().getResource()){
			houses.replaceItem(house, house.getId(), key, new CosmosItemRequestOptions());
			jedis.del(house.getId());
			String JedisKey = CACHE_HOUSE_TAG+house.getId();
			jedis.set(JedisKey, mapper.writeValueAsString(house));
			jedis.expire(JedisKey, TTL_CACHE);
		}catch(Exception e){
			LOG.severe(e.getMessage());
			LOG.severe("(2) deleteAllUserHouses: something went wrong while deleting a house");
			return false;
		}
		return true;
	}

	/** End of auxiliary methods **/


	/**
	 * Add user to the system
	 */
	public Response addUser(UserDAO user) {
		init(USERS);


		user.setPwd(DigestUtils.sha512Hex(user.getPwd()));
		user.setPhotoId("");
		LOG.warning("teste 0");
		LOG.warning(System.getenv("REDIS_HOSTNAME"));
		try(Jedis jedis = RedisCache.getCachePool().getResource()){
			LOG.warning("teste 1");
			CosmosItemResponse<UserDAO> res = users.createItem(user);
			//Long cnt = jedis.lpush("MostRecentUsers", mapper.writeValueAsString(user));
			ObjectMapper mapper = new ObjectMapper();
			LOG.warning("teste 2");

			String JedisKey = CACHE_USER_TAG+user.getId();
			jedis.set(JedisKey, mapper.writeValueAsString(user));
			LOG.warning("teste 3");

			jedis.expire(JedisKey, TTL_CACHE);
			LOG.warning("teste 4");

			return Response.status(res.getStatusCode()).entity(USER_CREATED_SUCCESS).build();

		} catch (Exception e) {
			LOG.warning(e.getMessage());
			return Response.status(409).entity(PROBLEM_OCCURRED_CREATE_USER).build();
		}
	}

	/**
	 * Update user information
	 */
	public Response updateUser(UserDAO user) {
		init(USERS);
		ObjectMapper mapper = new ObjectMapper();


		try(Jedis jedis = RedisCache.getCachePool().getResource()){
			String res = jedis.get(CACHE_USER_TAG+user.getId());

			// delete response from cache
			jedis.del(user.getId());
			PartitionKey key = new PartitionKey(user.getId());
			LOG.severe(res);
			if(res==null){

				CosmosItemResponse<UserDAO> response = users.readItem(user.getId(), key,
						new CosmosItemRequestOptions(),UserDAO.class);
				user.setPwd(response.getItem().getPwd());
				user.setPhotoId(response.getItem().getPhotoId());
				CosmosItemResponse<UserDAO> response1 = users.upsertItem(user, key, new CosmosItemRequestOptions());
				String JedisKey = CACHE_USER_TAG+user.getId();
				jedis.set(JedisKey, mapper.writeValueAsString(response1.getItem()));
				jedis.expire(JedisKey, TTL_CACHE);
				return Response.status(response1.getStatusCode()).build();

			}

			UserDAO userRedis = mapper.readValue(res, UserDAO.class);
			user.setPwd(userRedis.getPwd());
			user.setPhotoId(userRedis.getPhotoId());
			CosmosItemResponse<UserDAO> response1 = users.upsertItem(userRedis, key, new CosmosItemRequestOptions());
			String JedisKey2 = CACHE_USER_TAG+userRedis.getId();
			jedis.set(JedisKey2, mapper.writeValueAsString(userRedis));
			jedis.expire(JedisKey2, TTL_CACHE);
			return Response.status(response1.getStatusCode()).build();
		}
		catch(Exception e) {
			LOG.severe(e.getMessage());
			return Response.notModified().entity(PROBLEM_OCCURRED_UPDATE_USER).build();
		}
	}

	/**
	 * Get user by userId
	 */
	public Response getUserById(String id){
		init(USERS);

		try(Jedis jedis = RedisCache.getCachePool().getResource()){
			String res = jedis.get(CACHE_USER_TAG+id);
			ObjectMapper mapper = new ObjectMapper();
			if(res==null){
				PartitionKey key = new PartitionKey(id);
				CosmosItemResponse<UserDAO> response = users.readItem(id, key,
						new CosmosItemRequestOptions(),UserDAO.class);
				String JedisKey = CACHE_USER_TAG+id;
				jedis.set(JedisKey, mapper.writeValueAsString(response.getItem()));
				jedis.expire(JedisKey, TTL_CACHE);
				return Response.ok(response.getItem().toUser()).build();

			}
			UserDAO user = mapper.readValue(res, UserDAO.class);
			return Response.ok(user.toUser()).build();
		}
		catch (Exception e) {
			LOG.severe(e.getMessage());
			return Response.status(Response.Status.NOT_FOUND).entity(SOMETHING_WENT_WRONG).build();
		}

	}

	/**
	 * Get all users
	 */
	public Response getUsers(int page,int elements) {
		init(USERS);

		CosmosPagedIterable<UserDAO> usersList = users.queryItems("SELECT * FROM users OFFSET "+page*elements+" LIMIT "+elements, new CosmosQueryRequestOptions(), UserDAO.class);
		List<User> res = CosmosPagedIterableToArrayUserDAO(usersList);
		return Response.ok(res).build();
	}

	/**
	 * Change user password
	 */
	public Response changePassword(String userId, UserUpdatePwd updatePwd) {
		init(USERS);

		try{
			Response userResponse = getUserById(userId);
			UserDAO user = (UserDAO) userResponse.getEntity();

			if (user.getPwd().equals(DigestUtils.sha512Hex(updatePwd.getOldPwd()))) {

				if (updatePwd.getNewPwd().equals(updatePwd.getConfirmPwd())) {
					user.setPwd(DigestUtils.sha512Hex(updatePwd.getNewPwd()));
					PartitionKey key = new PartitionKey(userId);

					try{
						users.upsertItem(user, key, new CosmosItemRequestOptions());
						return Response.ok(PASSWORD_SUCCESS).build();
					}
					catch(Exception e){
						return Response.notModified(SOMETHING_WENT_WRONG).build();
					}

				}
				return Response.status(Response.Status.NOT_ACCEPTABLE).entity(PASSWORDS_DONT_MATCH).build();

			}
			return Response.status(Response.Status.FORBIDDEN).entity(WRONG_PASSWORD).build();

		}
		catch(Exception e){
			LOG.severe(e.getMessage());
			return Response.status(Response.Status.NOT_FOUND).entity(USER_NOT_FOUND).build();
		}
	}

	/**
	 * Auxiliary method. Turns a CosmosPagedIterable object of type UserDAO
	 * to a List of UserDAO
	 */
	private List<User> CosmosPagedIterableToArrayUserDAO(CosmosPagedIterable<UserDAO> results){
		List<User> usersList = new ArrayList<>();
		for(UserDAO userDAO : results){
			usersList.add(userDAO.toUser());
		}
		return usersList;
	}

	/**
	 * Auxiliary method. Turns a CosmosPagedIterable object of type RentalDAO
	 * to a List of RentalDAO
	 */
	private List<RentalDAO> CosmosPagedIterableToArrayRentalDAO(CosmosPagedIterable<RentalDAO> results){
		List<RentalDAO> rentalsList = new ArrayList<>();
		for(RentalDAO rentalDAO : results){
			rentalsList.add(rentalDAO);
		}
		return rentalsList;
	}

	/**
	 * Auxiliary method. Turns a CosmosPagedIterable object of type QuestionDAO
	 * to a List of QuestionDAO
	 */
	private List<QuestionDAO> CosmosPagedIterableToArrayQuestionDAO(CosmosPagedIterable<QuestionDAO> results) {
		List<QuestionDAO> questionsList = new ArrayList<>();
		for (QuestionDAO questionDAO : results) {
			questionsList.add(questionDAO);
		}
		return questionsList;
	}

	/**
	 * Auxiliary method. Turns a CosmosPagedIterable object of type HouseDAO
	 * to a List of HouseDAO
	 */
	private List<HouseDAO> CosmosPagedIterableToArrayHouseDAOToHouseDAO(CosmosPagedIterable<HouseDAO> results){
		List<HouseDAO> housesList = new ArrayList<>();
		for(HouseDAO houseDAO : results){
			housesList.add(houseDAO);
		}
		return housesList;
	}


	/** MEDIA **/

	/**
	 * Add user profile picture
	 */
	public String putUserFilenamePhoto(String filename,String userId){
		init(USERS);
		PartitionKey key = new PartitionKey(userId);

		try{
			CosmosItemResponse<UserDAO> response = users.readItem(userId, key, new CosmosItemRequestOptions(),UserDAO.class);

			UserDAO user = response.getItem();
			String OldPhoto = user.getPhotoId();
			user.setPhotoId(filename);

			users.upsertItem(user,key,new CosmosItemRequestOptions());
			try(Jedis jedis = RedisCache.getCachePool().getResource()){
				jedis.del(CACHE_USER_TAG+userId);
				ObjectMapper mapper = new ObjectMapper();
				String JedisKey = CACHE_USER_TAG+userId;
				jedis.set(JedisKey, mapper.writeValueAsString(user));
				jedis.expire(JedisKey, TTL_CACHE);
			}
			catch (Exception e){
				LOG.severe(e.getMessage());
			}
			return OldPhoto;
		}
		catch(Exception e){
			LOG.severe(e.getMessage());
			return "-1";
		}

	}

	/**
	 * Remove user profile picture
	 */
	public String removeUserPhoto(String userId){
		init(USERS);
		PartitionKey key = new PartitionKey(userId);

		try{

			CosmosItemResponse<UserDAO> response = users.readItem(userId, key,
					new CosmosItemRequestOptions(),UserDAO.class);

			UserDAO user = response.getItem();
			String OldPhoto = user.getPhotoId();
			user.setPhotoId("");

			users.upsertItem(user,key,new CosmosItemRequestOptions());
			try(Jedis jedis = RedisCache.getCachePool().getResource()){
				jedis.del(CACHE_USER_TAG+userId);
				ObjectMapper mapper = new ObjectMapper();
				String JedisKey = CACHE_USER_TAG+userId;
				jedis.set(JedisKey, mapper.writeValueAsString(user));
				jedis.expire(JedisKey, TTL_CACHE);

			}
			catch (Exception e){
				LOG.severe(e.getMessage());
			}
			return OldPhoto;
		}
		catch(Exception e){
			LOG.severe(e.getMessage());
			return "-1";
		}
	}

	/** LOGIN **/

	public Response login(UserLogin userData) {
		init(USERS);
		LOG.severe("username: "+userData.getUsername());
		PartitionKey key = new PartitionKey(userData.getUsername());

		try(Jedis jedis = RedisCache.getCachePool().getResource()){
			CosmosItemResponse<UserDAO> response = users.readItem(userData.getUsername(), key, new CosmosItemRequestOptions(),UserDAO.class);
			LOG.severe("login 1: ");
			UserDAO user = response.getItem();
			LOG.severe("login 2: ");
			if(user.getPwd().equals(DigestUtils.sha512Hex(userData.getPwd()))){
				LOG.warning("getting Token user:"+userData.getUsername());
				String uid = UUID.randomUUID().toString();
                int maxAgeToken = 3600;
				NewCookie cookie = new NewCookie.Builder("scc:session").value(uid).path("/").comment("sessionid").maxAge(maxAgeToken).secure(false).httpOnly(true).build();
				LOG.warning("RedisLayer.getInstance().putSession:");
                // Assuming Session class has getId and getUser methods
                jedis.set(uid, user.getId());
                // You might want to set an expiration time for the session key in Redis
                jedis.expire(uid, maxAgeToken);

				return Response.ok().cookie(cookie).build();
			}
			return Response.status(Response.Status.UNAUTHORIZED).entity(INVALID_PASSWORD).build();
		}
		catch (Exception e){
			LOG.severe(e.getMessage());
			return Response.status(Response.Status.UNAUTHORIZED).entity(SOMETHING_WENT_WRONG).build();
		}

	}
}
