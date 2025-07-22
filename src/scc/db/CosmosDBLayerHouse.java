package scc.db;

import com.azure.core.credential.AzureKeyCredential;
import com.azure.cosmos.*;
import com.azure.cosmos.models.CosmosItemRequestOptions;
import com.azure.cosmos.models.CosmosItemResponse;
import com.azure.cosmos.models.CosmosQueryRequestOptions;
import com.azure.cosmos.models.PartitionKey;
import com.azure.cosmos.util.CosmosPagedIterable;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.ws.rs.core.NewCookie;
import jakarta.ws.rs.core.Response;
import org.apache.commons.codec.digest.DigestUtils;
import org.glassfish.grizzly.utils.EchoFilter;
import redis.clients.jedis.Jedis;
import scc.cache.RedisCache;
import scc.data.*;
import scc.utils.Session;

import java.util.*;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class CosmosDBLayerHouse {

	/** CONSTANTS **/

    /** Cache Tags **/
	private static final String CACHE_HOUSE_TAG = "house:";
	private static final String CACHE_RENTAL_TAG = "rentals:";
	private static final String CACHE_QUESTION_TAG = "question:";
	private static final String ADMIN = "admin";

    /** Error messages **/

        /** house **/
    private static final String PROBLEM_OCCURRED_ADD_HOUSE = "A Problem occurred while adding a house";
    private static final String PROBLEM_OCCURRED_DELETE_HOUSE = "A Problem occurred while deleting a house";
    private static final String PROBLEM_OCCURRED_UPDATE_HOUSE = "A Problem occurred while updating a house";
    private static final String DISCOUNT_WAS_ALREADY = "The discount for this house was already ";
    private static final String PROBLEM_OCCURRED_TOGGLE_DISCOUNT = "A Problem occurred while toggling the discount for a house";
    private static final String UPDATING_DELETED_HOUSE = "This house was deleted. you can´t update any information about it";
    private static final String CANT_TOGGLE_DISCOUNT = "You can't toggle the discount for this house";
    private static final String HOUSE_NOT_FOUND = "House not found";
    private static final String UNAUTHORIZED_ADD_PHOTOS ="You can't add photos to this house";
    private static final String UNAUTHORIZED_HOUSE_ACCESS = "You can't update this house";
    private static final String UNAUTHORIZED_NOT_OWNER = "You are not the owner of the house";
    private static final String HOUSE_NOT_AVAILABLE_DATES = "The house isn't available for these dates";

        /** rental **/
	private static final String CANT_ACCESS_RENTAL = "You can´t access this rental";
    private static final String RENTAL_NOT_FOUND = "Rental not found";
    private static final String IMPOSSIBLE_DATES = "Initial date must be before final date";
	private static final String CANT_UPDATE_OLD_RENTALS =  "You can't update past rentals";
    private static final String INVALID_QUERY_RENTALS = "Either past or next has to be true";

        /** question **/
    private static final String PROBLEM_OCCURRED_ADD_QUESTION = "Problem creating the question";
    private static final String PROBLEM_OCCURRED_ADD_REPLY = "A problem occurred while trying to reply to the question";

        /** generic **/
    private static final String UNAUTHORIZED_OPERATION = "Unauthorized operation";
    private static final String SOMETHING_WRONG = "Something went wrong";
    private static final String GENERIC_NOT_FOUND = "Couldn't find it";


	/** Azure information **/
	private static final String CONNECTION_URL = System.getenv("CONNECTION_URL");
	private static final String DB_KEY = System.getenv("DB_KEY");
	private static final String DB_NAME = System.getenv("DB_NAME");


	/** Container names**/
	private static final String HOUSES = "houses";
	private static final String QUESTIONS = "questions";
	private static final String RENTALS = "rentals";

	/** TTL **/

	private static final int TTL_CACHE = 3600;

	private static CosmosDBLayerHouse instance;

	public static synchronized CosmosDBLayerHouse getInstance() {
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
		instance = new CosmosDBLayerHouse( client);
		return instance;

	}

	private CosmosClient client;
	private CosmosDatabase db;
	private CosmosContainer houses;
	private CosmosContainer questions;
	private CosmosContainer rentals;

	private static final Logger LOG = Logger.getLogger(CosmosDBLayerHouse.class.getName());

	public CosmosDBLayerHouse(CosmosClient client) {
		this.client = client;
	}
	
	private synchronized void init(String containerName) {
		if( db == null)
			db = client.getDatabase(DB_NAME);
		switch(containerName){
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

	/** HOUSES **/

	/**
	 * Add a new house to the system
	 */
	public Response addHouse(HouseDAO house) {
		init(HOUSES);

		Date date = new Date();
		house.setId("house-"+house.getOwnerId()+date.getTime());
		house.setActive(true);

        try{
            CosmosItemResponse<HouseDAO> responseItem = houses.createItem(house);

            try(Jedis jedis = RedisCache.getCachePool().getResource()){
                ObjectMapper mapper = new ObjectMapper();
				String JedisKey = CACHE_HOUSE_TAG+house.getId();
                jedis.set(JedisKey, mapper.writeValueAsString(house));
				jedis.expire(JedisKey, TTL_CACHE);
            } catch (Exception e) {
                LOG.severe(e.getMessage());
            }

            return Response.ok(responseItem.getItem()).build();
        }
        catch (Exception e){
            LOG.severe(e.getMessage());
            return Response.status(Response.Status.NOT_MODIFIED).entity(PROBLEM_OCCURRED_ADD_HOUSE).build();
        }
	}

	public Response delHouseById(String userId,String houseId) {
		init(HOUSES);

        try{
		    Response houseResponse = getHouseById(houseId);
			HouseDAO houseDAO = (HouseDAO) houseResponse.getEntity();

			if(houseDAO.getOwnerId().equals(userId) || userId.equals(ADMIN)){

				try(Jedis jedis = RedisCache.getCachePool().getResource()) {

					jedis.del(CACHE_HOUSE_TAG+houseId);
					PartitionKey key = new PartitionKey(houseId);
					CosmosItemResponse<Object> house =  houses.deleteItem(houseId, key, new CosmosItemRequestOptions());
					return Response.status(house.getStatusCode()).build();

				} catch (Exception e) {
					LOG.severe(e.getMessage());
				}

				return Response.status(400).entity(PROBLEM_OCCURRED_DELETE_HOUSE).build();
			}

			return Response.status(Response.Status.FORBIDDEN).entity(UNAUTHORIZED_HOUSE_ACCESS).build();
		}
        catch(Exception e){
            LOG.severe(e.getMessage());
            return Response.status(Response.Status.NOT_MODIFIED).entity(SOMETHING_WRONG).build();
        }

	}

	/**
	 * Update house information
	 */
	public Response updateHouse(String userId, HouseDAO house) {
        init(HOUSES);
        PartitionKey key = new PartitionKey(house.getId());

        try{

            CosmosItemResponse<HouseDAO> res = houses.readItem(house.getId(), key, new CosmosItemRequestOptions(), HouseDAO.class);

            HouseDAO h = res.getItem();

            if (!userId.equals(h.getOwnerId()) && !userId.equals(ADMIN)) {
                return Response.status(Response.Status.FORBIDDEN).entity(UNAUTHORIZED_HOUSE_ACCESS).build();
            }

            if (h.isActive()) {
                HouseDAO newHouse = new HouseDAO(h.getId(), house.getName(), house.getLocation(), house.getDescription(),
                        h.getPhotoIds(), house.getPrice(), house.getDiscount_price(), h.getOwnerId(), h.isOnDiscount(), h.isActive());

                houses.replaceItem(newHouse, h.getId(), key, new CosmosItemRequestOptions());

                try (Jedis jedis = RedisCache.getCachePool().getResource()) {

                    jedis.del(CACHE_HOUSE_TAG + house.getId());
                    ObjectMapper mapper = new ObjectMapper();
					String JedisKey = CACHE_HOUSE_TAG+house.getId();
                    jedis.set(JedisKey, mapper.writeValueAsString(newHouse));
					jedis.expire(JedisKey, TTL_CACHE);

                } catch (Exception e) {
                    LOG.severe(e.getMessage());
                }
                return Response.ok(newHouse).build();
            }
            return Response.status(Response.Status.UNAUTHORIZED).entity(UPDATING_DELETED_HOUSE).build();

        }
        catch(Exception e){
            LOG.severe(e.getMessage());
            return Response.status(Response.Status.NOT_MODIFIED).entity(PROBLEM_OCCURRED_UPDATE_HOUSE).build();
        }
	}

	/**
	 * Toggle discount on or off.
	 */
	public Response toggleDiscount(String userId,String houseId, boolean discount) {
        init(HOUSES);


        PartitionKey key = new PartitionKey(houseId);
        try{
            CosmosItemResponse<HouseDAO> res = houses.readItem(houseId, key, new CosmosItemRequestOptions(), HouseDAO.class);


            HouseDAO h = res.getItem();

            if (h.isOnDiscount() == discount) {
                return Response.status(Response.Status.OK).entity(DISCOUNT_WAS_ALREADY + discount).build();
            }

            if (!userId.equals(h.getOwnerId())) {
                return Response.status(Response.Status.FORBIDDEN).entity(CANT_TOGGLE_DISCOUNT).build();
            }

            HouseDAO newHouse = new HouseDAO(h.getId(), h.getName(), h.getLocation(), h.getDescription(),
                    h.getPhotoIds(), h.getPrice(), h.getDiscount_price(), h.getOwnerId(), discount, h.isActive());

            houses.replaceItem(newHouse, houseId, key, new CosmosItemRequestOptions());

            try (Jedis jedis = RedisCache.getCachePool().getResource()) {

                jedis.del(CACHE_HOUSE_TAG + houseId);
                ObjectMapper mapper = new ObjectMapper();
				String JedisKey = CACHE_HOUSE_TAG + houseId;
                jedis.set(JedisKey, mapper.writeValueAsString(newHouse));
				jedis.expire(JedisKey, TTL_CACHE);

            } catch (Exception e) {
                LOG.severe(e.getMessage());
            }
            return Response.ok(newHouse).build();

        }
        catch(Exception e){
            LOG.severe(e.getMessage());
            return Response.status(Response.Status.NOT_MODIFIED).entity(PROBLEM_OCCURRED_TOGGLE_DISCOUNT).build();
        }
	}

	/**
	 * Get a house by its id
	 */
	public Response getHouseById( String id) {
		init(HOUSES);
		try(Jedis jedis = RedisCache.getCachePool().getResource()){
			String res = jedis.get(CACHE_HOUSE_TAG+id);

            ObjectMapper mapper = new ObjectMapper();

			if(res==null){
				PartitionKey key = new PartitionKey(id);
				CosmosItemResponse<HouseDAO> response = houses.readItem(id, key, new CosmosItemRequestOptions(),HouseDAO.class);

				HouseDAO house = response.getItem();
				String JedisKey = CACHE_HOUSE_TAG+id;
				jedis.set(JedisKey, mapper.writeValueAsString(house));
				jedis.expire(JedisKey, TTL_CACHE);
				return Response.ok(house.toHouse()).build();

			}

			HouseDAO houseDao = mapper.readValue(res, HouseDAO.class);
			return Response.ok(houseDao).build();
		}
		catch (Exception e) {
			LOG.severe(e.getMessage());
			return Response.status(Response.Status.NOT_FOUND).entity(HOUSE_NOT_FOUND).build();
		}
	}

	/**
	 * Get all the houses in the system
	 */
	public Response getHouses(int page,int elements) {
		init(HOUSES);

		CosmosPagedIterable <HouseDAO> housesList = this.houses.queryItems("SELECT * FROM houses WHERE houses.active=true OFFSET "+page*elements+" LIMIT "+elements, new CosmosQueryRequestOptions(), HouseDAO.class);
		return Response.ok(CosmosPagedIterableToArrayHouseDAO(housesList)).build();
	}

	/**
	 * Get all the houses owned by a specific user
	 */
	public Response getHousesByUser(String userId,int page,int elements){
		init(HOUSES);
		CosmosPagedIterable <HouseDAO> housesList =  houses.queryItems("SELECT * FROM houses WHERE houses.active=true AND houses.ownerId=\"" + userId + "\" OFFSET "+page*elements+" LIMIT "+elements, new CosmosQueryRequestOptions(), HouseDAO.class);
		return Response.ok(CosmosPagedIterableToArrayHouseDAO(housesList)).build();
	}

	/**
	 * Get all the houses in a given location
	 */
	public Response getHousesByLocation(String location,int page,int elements){
		init(HOUSES);
		CosmosPagedIterable <HouseDAO> housesList = houses.queryItems("SELECT * FROM houses WHERE houses.active=true AND houses.location=\"" + location + "\" OFFSET "+page*elements+" LIMIT "+elements, new CosmosQueryRequestOptions(), HouseDAO.class);
		return Response.ok(CosmosPagedIterableToArrayHouseDAO(housesList)).build();
	}

	/**
	 * Get all the houses available for a given time period and in a specific location
	 */
	public Response searchForAvailableHousesByDateAndLocation(long inicial_date, long final_date,
															  String location,int page,int elements){
		init(RENTALS);

		if(inicial_date>=final_date){
			return Response.status(Response.Status.BAD_REQUEST).entity(IMPOSSIBLE_DATES).build();
		}

		CosmosPagedIterable<RentalDAO> rentalListOnPeriodOfTime =
				rentals.queryItems("SELECT * FROM rentals WHERE rentals.location=\"" + location +
								"\" AND (rentals.inicial_date <= " + inicial_date + " AND rentals.final_date >= " +
								final_date +") OR (rentals.inicial_date >= " + inicial_date + " AND rentals.final_date <= " +
								final_date +") OR (rentals.inicial_date <= " + inicial_date + " AND rentals.final_date <= " +
								final_date + " AND rentals.final_date >=" + inicial_date + ") OR (rentals.inicial_date >= " + inicial_date + " AND rentals.final_date >= " +
								final_date+" AND rentals.inicial_date <=" + final_date + ")",
						new CosmosQueryRequestOptions(), RentalDAO.class);

		List<RentalDAO> rentalsList = CosmosPagedIterableToArrayRentalDAO(rentalListOnPeriodOfTime);

		Set<String> rentedHousesList = new HashSet<>();
		for(RentalDAO rental:rentalsList){
			rentedHousesList.add(rental.getHouseId());
		}

		init(HOUSES);
		CosmosPagedIterable<HouseDAO> housesByLocationList = houses.queryItems("SELECT * FROM houses WHERE houses.active = true AND houses.location=\"" + location +
				"\"", new CosmosQueryRequestOptions(), HouseDAO.class);

		List<House> hList = CosmosPagedIterableToArrayHouseDAO(housesByLocationList);

        hList.removeIf(house -> rentedHousesList.contains(house.getId()));

		return Response.ok(hList).build();
	}


	/**
	 * Get all the houses in discount
	 */
	public Response getNextDiscountedHouses(int page, int elements){
		init(HOUSES);
		CosmosPagedIterable<HouseDAO> housesList = houses.queryItems("SELECT * FROM houses WHERE houses.active = true AND houses.onDiscount = true OFFSET " + page*elements + " LIMIT " + elements,
				new CosmosQueryRequestOptions(), HouseDAO.class);
		return Response.ok(CosmosPagedIterableToArrayHouseDAO(housesList)).build();
	}

	/**
	 * Auxiliary method. Turns a CosmosPagedIterable object of type HouseDAO
	 * to a List of HouseDAO
	 */
	private List<House> CosmosPagedIterableToArrayHouseDAO(CosmosPagedIterable<HouseDAO> results){
		List<House> housesList = new ArrayList<>();
		for(HouseDAO houseDAO : results){
			housesList.add(houseDAO.toHouse());
		}
		return housesList;
	}

	/** HOUSE MEDIA **/

	/**
	 * Add a photo to the list of photos of a house
	 */
	public Response addHousePhoto(String userId,String filename, String houseId){
		init(HOUSES);
		PartitionKey key = new PartitionKey(houseId);

        try{
            CosmosItemResponse<HouseDAO> response = houses.readItem(houseId, key, new CosmosItemRequestOptions(),HouseDAO.class);
            HouseDAO house = response.getItem();

            if(house.getOwnerId().equals(userId)){
                house.addPhotoFilename(filename);
                CosmosItemResponse<HouseDAO> h = houses.upsertItem(house,key,new CosmosItemRequestOptions());

                try(Jedis jedis = RedisCache.getCachePool().getResource()){
                    jedis.del(CACHE_HOUSE_TAG+house.getId());
                    ObjectMapper mapper = new ObjectMapper();
					String JedisKey = CACHE_HOUSE_TAG+house.getId();
                    jedis.set(JedisKey, mapper.writeValueAsString(h));
					jedis.expire(JedisKey, TTL_CACHE);
                }
                catch (Exception e){
                    LOG.severe(e.getMessage());
                }
                return Response.ok(h.getItem().toHouse()).build();
            }

            return Response.status(Response.Status.FORBIDDEN).entity(UNAUTHORIZED_ADD_PHOTOS).build();
        }
        catch(Exception e){
            LOG.severe(e.getMessage());
            return Response.status(Response.Status.NOT_MODIFIED).build();
        }
	}

	/**
	 * remove a photo of the list of photos of a house
	 */
	public String removeHousePhoto(String userId, String filename, String houseId){
		init(HOUSES);

        try{
            Response res = getHouseById(houseId);

            HouseDAO house = (HouseDAO) res.getEntity();

            if (!userId.equals(house.getOwnerId()) && !userId.equals(ADMIN)){
                return "-1";
            }

            boolean result = house.removePhotoFilename(filename);
            if(result){
                try(Jedis jedis = RedisCache.getCachePool().getResource()){
                    jedis.del(CACHE_HOUSE_TAG+house.getId());
                    ObjectMapper mapper = new ObjectMapper();
					String JedisKey = CACHE_HOUSE_TAG+house.getId();
                    jedis.set(JedisKey, mapper.writeValueAsString(house));
					jedis.expire(JedisKey, TTL_CACHE);

                }
                catch (Exception e){
                    LOG.severe(e.getMessage());
                }
                houses.upsertItem(house);

                return filename;
            }
            return "";
        }
        catch (Exception e){
            LOG.severe(e.getMessage());
            return "-1";
        }

	}

	/** QUESTIONS **/

	/**
	 * Ask a question
	 */
	public Response addQuestion(QuestionDAO question) {
		init(QUESTIONS);

		Date date = new Date();
		question.setId("question-"+question.getHouseId()+date.getTime());

        Response r;
        try{
            r = getHouseById(question.getHouseId());
        }
        catch (Exception e){
            LOG.severe(e.getMessage());
            return Response.status(Response.Status.NOT_FOUND).entity(HOUSE_NOT_FOUND).build();
        }


        try{
            CosmosItemResponse<QuestionDAO> response = questions.createItem(question);

            try(Jedis jedis = RedisCache.getCachePool().getResource()){
                ObjectMapper mapper = new ObjectMapper();
				String JedisKey = CACHE_QUESTION_TAG+question.getId();
                jedis.set(JedisKey, mapper.writeValueAsString(question));
				jedis.expire(JedisKey, TTL_CACHE);
            } catch (Exception e) {
                LOG.severe(e.getMessage());
            }
            return Response.ok(response.getItem()).build();
        }
        catch (Exception e){
            LOG.severe(e.getMessage());
            return Response.status(Response.Status.NOT_MODIFIED).entity(PROBLEM_OCCURRED_ADD_QUESTION).build();
        }
	}

	/**
	 * Get all the questions associated to a house
	 */
	public Response getQuestions(String houseId,int page,int elements) {
		init(QUESTIONS);
		CosmosPagedIterable <QuestionDAO> questionsList = questions.queryItems("SELECT * FROM questions WHERE questions.houseId=\"" + houseId + "\" OFFSET "+page*elements+" LIMIT "+elements, new CosmosQueryRequestOptions(), QuestionDAO.class);
		return Response.ok(CosmosPagedIterableToArrayQuestionDAO(questionsList)).build();
	}

	/**
	 * Auxiliary method. Turns a CosmosPagedIterable object of type QuestionDAO
	 * to a List of QuestionDAO
	 */
	private List<QuestionDAO> CosmosPagedIterableToArrayQuestionDAO(CosmosPagedIterable<QuestionDAO> results){
		List<QuestionDAO> questionsList = new ArrayList<>();
		for(QuestionDAO questionDAO : results){
			questionsList.add(questionDAO);
		}
		return questionsList;
	}

	/**
	 * Get a specific question
	 */
	public Response getQuestionById(String questionId){
		init(QUESTIONS);
		try(Jedis jedis = RedisCache.getCachePool().getResource()){

			String res = jedis.get(CACHE_QUESTION_TAG+questionId);
			ObjectMapper mapper = new ObjectMapper();

			if(res==null){

				PartitionKey key = new PartitionKey(questionId);
				CosmosItemResponse<QuestionDAO> response = questions.readItem(questionId, key, new CosmosItemRequestOptions(),QuestionDAO.class);

                QuestionDAO question = response.getItem();
				String JedisKey = CACHE_QUESTION_TAG+questionId;
                jedis.set(JedisKey, mapper.writeValueAsString(question));
				jedis.expire(JedisKey, TTL_CACHE);

                return Response.ok(question).build();

			}

			QuestionDAO question = mapper.readValue(res, QuestionDAO.class);
			return Response.ok(question).build();
		}
		catch (Exception e) {
			LOG.severe(e.getMessage());
            return Response.ok(Response.Status.NOT_FOUND).entity(SOMETHING_WRONG).build();
		}
	}

	/**
	 * Reply to a question. Exclusive to the owner of the house
	 */
	public Response replyToQuestion(String ownerId, String questionId, String answer){
		init(QUESTIONS);

        try{
            Response QuestionObject = getQuestionById(questionId);

            QuestionDAO questionItem = (QuestionDAO) QuestionObject.getEntity();

            Response HouseObject = getHouseById(questionItem.getHouseId());

            HouseDAO houseItem = (HouseDAO) HouseObject.getEntity();

            if (houseItem.getOwnerId().equals(ownerId)){

                questionItem.setReply(answer);

                try{
                    questions.upsertItem(questionItem);
                }
                catch(Exception e){
                    return Response.status(Response.Status.NOT_MODIFIED).entity(PROBLEM_OCCURRED_ADD_REPLY).build();
                }

                try(Jedis jedis = RedisCache.getCachePool().getResource()){
                    jedis.del(CACHE_QUESTION_TAG+questionId);
                    ObjectMapper mapper = new ObjectMapper();
					String JedisKey = CACHE_QUESTION_TAG+questionId;
                    jedis.set(JedisKey, mapper.writeValueAsString(questionItem));
					jedis.expire(JedisKey, TTL_CACHE);
                } catch (Exception e) {
                    LOG.severe(e.getMessage());
                }

                return Response.ok(questionItem).build();
            }
            return Response.status(Response.Status.FORBIDDEN).entity(UNAUTHORIZED_NOT_OWNER).build();
        }
        catch (Exception e){
            LOG.severe(e.getMessage());
            return Response.status(Response.Status.NOT_FOUND).entity(GENERIC_NOT_FOUND).build();
        }
	}

	/** RENTAL **/

	/**
	 * Create a rental for a house
	 */
	public Response createRental(RentalDAO rental) {
		init(RENTALS);
        Response houseResponse;
        try{
            houseResponse = getHouseById(rental.getHouseId());
        }
        catch (Exception e){
            LOG.severe(e.getMessage());
            return Response.status(Response.Status.NOT_FOUND).entity(SOMETHING_WRONG).build();
        }

		HouseDAO house = (HouseDAO)houseResponse.getEntity();
		Date date = new Date();
		rental.setId("rental-"+rental.getHouseId()+date.getTime());
		rental.setLocation(house.getLocation());

		if(isHouseAvailable(house,rental.getInicial_date().getTime(),rental.getFinal_date().getTime())){

            try{
                CosmosItemResponse<RentalDAO> response = rentals.createItem(rental);

                try(Jedis jedis = RedisCache.getCachePool().getResource()){

                    ObjectMapper mapper = new ObjectMapper();
					String JedisKey = CACHE_RENTAL_TAG+rental.getId();
                    jedis.set(JedisKey, mapper.writeValueAsString(rental));
					jedis.expire(JedisKey, TTL_CACHE);

                } catch (Exception e) {
                    LOG.severe(e.getMessage());
                }
                return Response.ok(response.getItem()).build();

            }
            catch (Exception e){
                LOG.severe(e.getMessage());
                return Response.status(Response.Status.NOT_MODIFIED).entity(SOMETHING_WRONG).build();
            }

		}
		return Response.status(Response.Status.CONFLICT).entity(HOUSE_NOT_AVAILABLE_DATES).build();
	}

	/**
	 * Auxiliary method. True is a house is available for the dates given
	 */
	private boolean isHouseAvailable(HouseDAO house,long inicial_date, long final_date){

		PartitionKey key = new PartitionKey(house.getId());
		HouseDAO h = houses.readItem(house.getId(), key, new CosmosItemRequestOptions(), HouseDAO.class).getItem();

		if(h.isActive()) {
			CosmosPagedIterable<RentalDAO> rentalListOnPeriodOfTime =
					rentals.queryItems("SELECT * FROM rentals WHERE  rentals.houseId=\"" + house.getId() + "\" " +
									"AND (rentals.inicial_date <= " + inicial_date + " AND rentals.final_date >= " +
									final_date + ") OR (rentals.inicial_date >= " + inicial_date + " AND rentals.final_date <= " +
									final_date + ") OR (rentals.inicial_date <= " + inicial_date + " AND rentals.final_date <= " +
									final_date + " AND rentals.final_date >=" + inicial_date + ") OR (rentals.inicial_date >= " + inicial_date + " AND rentals.final_date >= " +
									final_date + " AND rentals.inicial_date <=" + final_date + ")"
							, new CosmosQueryRequestOptions(), RentalDAO.class);

			List<RentalDAO> rentalsList = CosmosPagedIterableToArrayRentalDAO(rentalListOnPeriodOfTime);
			return rentalsList.isEmpty();
		}
		return false;
	}

	/**
	 * Get all the rentals associated to a house
	 */
	public Response listRental(String userId,String houseId,int page, int elements) {
		init(RENTALS);

		Response houseResponse = getHouseById(houseId);
		HouseDAO house = (HouseDAO) houseResponse.getEntity();

		if(userId.equals(house.getOwnerId()) || userId.equals(ADMIN)){
			CosmosPagedIterable<RentalDAO> rentalResult = rentals.queryItems("SELECT * FROM rentals WHERE rentals.houseId=\"" + houseId + "\" OFFSET "+page*elements+" LIMIT "+elements, new CosmosQueryRequestOptions(), RentalDAO.class);
			return Response.ok(CosmosPagedIterableToArrayRentalDAO(rentalResult)).build();
		}
		return Response.status(Response.Status.UNAUTHORIZED).entity(UNAUTHORIZED_OPERATION).build();
	}

	/**
	 * Get a rental
	 */
	public Response getRentalById(String userId,String rentalId) {
		init(RENTALS);

		try(Jedis jedis = RedisCache.getCachePool().getResource()) {
			String res = jedis.get(CACHE_RENTAL_TAG+rentalId);
			ObjectMapper mapper = new ObjectMapper();

			if(res==null){

				PartitionKey key = new PartitionKey(rentalId);
                try{

                    CosmosItemResponse<RentalDAO> response = rentals.readItem(rentalId, key, new CosmosItemRequestOptions(), RentalDAO.class);
                    RentalDAO rent = response.getItem();

                    if(verifyAuthorizationRental(userId,rent,true)){
						String JedisKey = CACHE_RENTAL_TAG+rentalId;
                        jedis.set(JedisKey, mapper.writeValueAsString(rent));
						jedis.expire(JedisKey, TTL_CACHE);
                        return Response.ok(res).build();
                    }

                    return Response.status(Response.Status.UNAUTHORIZED).entity(CANT_ACCESS_RENTAL).build();
                }
                catch (Exception e){
                    LOG.severe(e.getMessage());
                    return Response.status(Response.Status.NOT_FOUND).entity(RENTAL_NOT_FOUND).build();
                }
			}

			RentalDAO rental = mapper.readValue(res, RentalDAO.class);
			if(verifyAuthorizationRental(userId,rental,true)){
				return Response.ok(rental).build();
			}

			return Response.status(Response.Status.UNAUTHORIZED).entity(CANT_ACCESS_RENTAL).build();

		} catch (Exception e) {
			LOG.severe(e.getMessage());
            return Response.status(Response.Status.NOT_FOUND).entity(SOMETHING_WRONG).build();
		}

	}

	/**
	 * Get all the rentals made by a user. Can be filtered to include only past rentals, future rentals or both
	 */
	public Response listUserRentals(String userId, boolean past, boolean next, int page, int elements){
		init(RENTALS);
		CosmosPagedIterable<RentalDAO> rentalResult;
		if(past && next){
			rentalResult = rentals.queryItems("SELECT * FROM rentals WHERE rentals.renterId=\"" + userId
					+ "\" OFFSET "+page*elements+" LIMIT "+elements, new CosmosQueryRequestOptions(), RentalDAO.class);
		}
		else if(past){
			Date date = new Date();
			rentalResult = rentals.queryItems("SELECT * FROM rentals WHERE rentals.final_date <" + date.getTime() + " AND rentals.renterId=\"" + userId
					+ "\" OFFSET "+page*elements+" LIMIT "+elements, new CosmosQueryRequestOptions(), RentalDAO.class);
		}
		else if(next){
			Date date = new Date();
			rentalResult = rentals.queryItems("SELECT * FROM rentals WHERE rentals.final_date >=" + date.getTime() + " AND rentals.renterId=\"" + userId
					+ "\" OFFSET "+page*elements+" LIMIT "+elements, new CosmosQueryRequestOptions(), RentalDAO.class);
		}
		else
			return Response.status(Response.Status.BAD_REQUEST).entity(INVALID_QUERY_RENTALS).build();
		return Response.ok(CosmosPagedIterableToArrayRentalDAO(rentalResult)).build();
	}

	/**
	 * Verifies if a user is allowed to perform an action
	 */
	private boolean verifyAuthorizationRental(String userId,RentalDAO rent,boolean adminPermission){

        try{
            Response houseResponse = getHouseById(rent.getHouseId());

            HouseDAO houseDAO = (HouseDAO) houseResponse.getEntity();

            if(!adminPermission && userId.equals(houseDAO.getOwnerId()) || userId.equals(rent.getRenterId())) {
                return true;
            }
            return (adminPermission && userId.equals(houseDAO.getOwnerId()) || userId.equals(rent.getRenterId()) || userId.equals(ADMIN) );
        }
        catch (Exception e){
            LOG.severe(e.getMessage());
            return false;
        }
	}

	/**
	 * Update rental information. Exclusive for future rentals
	 */
	public Response updateRental(String userId,RentalDAO rental) {
		init(RENTALS);

		String rentalId = rental.getId();
		PartitionKey key = new PartitionKey(rentalId);
        CosmosItemResponse<RentalDAO> res;

        try{
            res = rentals.readItem(rentalId, key, new CosmosItemRequestOptions(),RentalDAO.class);
        }
        catch (Exception e){
            LOG.severe(e.getMessage());
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(RENTAL_NOT_FOUND).build();
        }

		RentalDAO rent = res.getItem();
		Date date = new Date();

		if(rent.getInicial_date().getTime()<= date.getTime()){
			return Response.status(Response.Status.FORBIDDEN).entity(CANT_UPDATE_OLD_RENTALS).build();
		}

		if(verifyAuthorizationRental(userId,rent,false)) {

			RentalDAO newRental = new RentalDAO(rentalId, rent.getPrice(), rent.getHouseId(), rent.getRenterId(), rent.getLocation(),
					rental.getInicial_date(), rental.getFinal_date());

			try (Jedis jedis = RedisCache.getCachePool().getResource()) {

                rentals.replaceItem(newRental, rentalId, key, new CosmosItemRequestOptions());

				jedis.del(CACHE_RENTAL_TAG + rentalId);
				ObjectMapper mapper = new ObjectMapper();
				String JedisKey = CACHE_RENTAL_TAG + rentalId;
				jedis.set(JedisKey, mapper.writeValueAsString(newRental));
				jedis.expire(JedisKey, TTL_CACHE);
                return Response.ok(newRental).build();

			} catch (Exception e) {
				LOG.severe(e.getMessage());
                return Response.status(Response.Status.NOT_MODIFIED).entity(SOMETHING_WRONG).build();
			}
		}
		return Response.status(Response.Status.UNAUTHORIZED).entity(UNAUTHORIZED_OPERATION).build();
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
}
