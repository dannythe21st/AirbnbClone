package scc.srv;

import com.azure.core.credential.AzureKeyCredential;
import com.azure.search.documents.SearchClient;
import com.azure.search.documents.SearchClientBuilder;
import com.azure.search.documents.SearchDocument;
import com.azure.search.documents.models.SearchMode;
import com.azure.search.documents.models.SearchOptions;
import com.azure.search.documents.util.SearchPagedIterable;
import com.azure.search.documents.util.SearchPagedResponse;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Cookie;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import redis.clients.jedis.Jedis;
import scc.cache.RedisCache;
import scc.data.HouseDAO;
import scc.utils.Session;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;


/**
 * Resource for managing Users.
 */

@Path("/cog")
public class CogResource
{
	private static final Logger LOG = Logger.getLogger(CogResource.class.getName());

	public static final String PROP_SERVICE_NAME = "PROP_SERVICE_NAME";
	public static final String PROP_SERVICE_URL = "PROP_SERVICE_URL";
	public static final String PROP_INDEX_NAME = "PROP_INDEX_NAME";
	public static final String PROP_QUERY_KEY = "PROP_QUERY_KEY";


	public CogResource() {

	}


	/**
	 * Update a user
	 */
	@GET
	@Path("/")
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.APPLICATION_JSON)
	public Response updateUser(@CookieParam("scc:session") Cookie session,@QueryParam("text") String text,@QueryParam("page") int page) {
		try {
			checkCookieUserGetUserId(session);
			return searchCog(text,page);
		}
		catch( WebApplicationException e) { throw e; }
		catch( Exception e) { throw new InternalServerErrorException(e); }

	}

	public Response searchCog(String queryText,int page) {
		try {

			// WHEN READING THE PROPERTIES SET IN AZURE
			SearchClient searchClient = new SearchClientBuilder()
					.credential(new AzureKeyCredential(System.getenv(PROP_QUERY_KEY)))
					.endpoint(System.getenv(PROP_SERVICE_URL)).indexName(System.getenv(PROP_INDEX_NAME))
					.buildClient();


			SearchOptions options = new SearchOptions().setIncludeTotalCount(true)
					.setSelect("id", "ownerId", "name", "location", "description", "price" , "discount_price","onDiscount","photoIds").setSearchFields("name", "description","location")
					.setFilter("active eq true")
					.setSearchMode(SearchMode.ALL)
					.setTop(5).setSkip(page);

			SearchPagedIterable searchPagedIterable = searchClient.search(queryText, options, null);

			System.out.println("Number of results : " + searchPagedIterable.getTotalCount());

			List<HouseDAO> list = new ArrayList<>(5);

			for (SearchPagedResponse resultResponse : searchPagedIterable.iterableByPage()) {
				resultResponse.getValue().forEach(searchResult -> {
					HouseDAO house = new HouseDAO();

					house.setId(searchResult.getDocument(SearchDocument.class).get("id").toString());
					house.setOwnerId(searchResult.getDocument(SearchDocument.class).get("ownerId").toString());
					house.setName(searchResult.getDocument(SearchDocument.class).get("name").toString());
					house.setLocation(searchResult.getDocument(SearchDocument.class).get("location").toString());
					house.setDescription(searchResult.getDocument(SearchDocument.class).get("description").toString());

					house.setPrice(Double.parseDouble(searchResult.getDocument(SearchDocument.class).get("price").toString()));
					house.setDiscount_price(Double.parseDouble(searchResult.getDocument(SearchDocument.class).get("discount_price").toString()));

					if(searchResult.getDocument(SearchDocument.class).get("onDiscount").toString().equals("true")){
						LOG.severe("ON DISCOUNT BBROOOOOOOO");
						house.setOnDiscount(true);
					}
					else{
						LOG.severe("NO DISCOUNT BBROOOOOOOO");
						house.setOnDiscount(false);
					}

					List<String> photoIds = (List<String>) searchResult.getDocument(SearchDocument.class).get("photoIds");
					house.setPhotoIds(photoIds);
					house.setActive(true);

					list.add(house);
				});
			}
			return Response.ok(list).build();
		} catch (Exception e) {
			LOG.severe(e.getMessage());
			return Response.status(Response.Status.BAD_REQUEST).entity("Something went wrong with the search").build();
		}
	}


	public String checkCookieUserGetUserId(Cookie session) throws NotAuthorizedException {
		if (session == null || session.getValue() == null)
			throw new NotAuthorizedException("No session initialized");
		Session s;
		LOG.warning("session: "+session);
		LOG.warning("session getValue: "+session.getValue());
		try(Jedis jedis = RedisCache.getCachePool().getResource()){
			String sessionId = session.getValue();
			String user = jedis.get(sessionId);
			if (user != null) {
				s = new Session(sessionId, user);
			} else {
				s = null; // Session not found
			}
		} catch (Exception e) {
			throw new NotAuthorizedException("No valid session initialized (1)");
		}
		LOG.warning("getting Cookie: "+s);
		if (s == null || s.getUser() == null || s.getUser().isEmpty())
			throw new NotAuthorizedException("No valid session initialized (2)");
		return s.getUser();
	}

}
