package scc.srv;

import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Cookie;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import redis.clients.jedis.Jedis;
import scc.cache.RedisCache;
import scc.data.UserDAO;
import scc.data.UserLogin;
import scc.data.UserUpdatePwd;
import scc.db.CosmosDBLayerUsers;
import scc.utils.Session;

import java.util.logging.Logger;


/**
 * Resource for managing Users.
 */

@Path("/user")
public class UserResource
{
	private CosmosDBLayerUsers cosmosDB;
	private static final Logger LOG = Logger.getLogger(UserResource.class.getName());


	public UserResource() {
		this.cosmosDB = CosmosDBLayerUsers.getInstance();
	}


	/**
	 * Delete user by id
	 */
	@DELETE
	@Path("/admin/{id}")
	@Produces(MediaType.APPLICATION_JSON)
	public Response delUserByIdADMIN(@CookieParam("scc:session") Cookie session,@PathParam("id") String userId) {
		try {
			checkCookieUser(session, "admin");
			return cosmosDB.delUserById(userId);
		}
		catch( WebApplicationException e) { throw e; }
		catch( Exception e) { throw new InternalServerErrorException(e); }

	}

	/**
	 * Delete user by id
	 */

	@DELETE
	@Path("/delete")
	@Produces(MediaType.APPLICATION_JSON)
	public Response delMyAccount(@CookieParam("scc:session") Cookie session) {
		try {
			String userId = checkCookieUserGetUserId(session);
			return cosmosDB.delUserById(userId);
		}
		catch( WebApplicationException e) { throw e; }
		catch( Exception e) { throw new InternalServerErrorException(e); }

	}


	@POST
	@Path("/login")
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.APPLICATION_JSON)
	public Response login(UserLogin userData) {
		return cosmosDB.login(userData);
	}

	/**
	 * Add a new user
	 */
	@POST
	@Path("/create")
	@Consumes(MediaType.APPLICATION_JSON)
	public Response addUser(UserDAO user) {
		return cosmosDB.addUser(user);
	}

	/**
	 * Update a user
	 */
	@PUT
	@Path("/")
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.APPLICATION_JSON)
	public Response updateUser(@CookieParam("scc:session") Cookie session,UserDAO user) {
		try {
			checkCookieUser(session, user.getId());
		}
		catch( WebApplicationException e) { throw e; }
		catch( Exception e) { throw new InternalServerErrorException(e); }

		return cosmosDB.updateUser(user);
	}


	/**
	 * get user by id
	 */
	@GET
	@Path("/get/{id}")
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.APPLICATION_JSON)
	public Response getUserById(@CookieParam("scc:session") Cookie session,
								@PathParam("id") String userId) {
		try {
			checkCookieUser(session, userId);
		}
		catch( WebApplicationException e) { throw e; }
		catch( Exception e) { throw new InternalServerErrorException(e); }

		return cosmosDB.getUserById(userId);
	}

	/**
	 * Lists all the users.
	 */
	@GET
	@Path("/list")
	@Produces(MediaType.APPLICATION_JSON)
	public Response getUsers(@CookieParam("scc:session") Cookie session,
							 @QueryParam("page") int page,@QueryParam("elements") int elements) {
		try {
			checkCookieUser(session, "admin");
			return cosmosDB.getUsers(page,elements);
			}
		catch( WebApplicationException e) { throw e; }
		catch( Exception e) { throw new InternalServerErrorException(e); }
	}

	@PUT
	@Path("/changepassword")
	@Produces(MediaType.APPLICATION_JSON)
	public Response changePassword(@CookieParam("scc:session") Cookie session, UserUpdatePwd updatePwd) {
		try{
			return cosmosDB.changePassword(checkCookieUserGetUserId(session),updatePwd);
		}
		catch( WebApplicationException e) { throw e; }
		catch( Exception e) { throw new InternalServerErrorException(e); }
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

	public Session checkCookieUser(Cookie session, String id) throws NotAuthorizedException {
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

		if (!s.getUser().equals(id) && !s.getUser().equals("admin"))
			throw new NotAuthorizedException("Invalid user : " + s.getUser());

		LOG.fine(s.getUser());
		LOG.fine(s.getId());
		return s;
	}
}
