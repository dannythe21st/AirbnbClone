package scc.srv;

import com.azure.core.util.BinaryData;
import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobContainerClientBuilder;

import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Cookie;
import redis.clients.jedis.Jedis;
import scc.cache.RedisCache;
import scc.db.CosmosDBLayerHouse;
import scc.db.CosmosDBLayerUsers;

import scc.utils.Hash;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import scc.utils.Session;

/**
 * Resource for managing media files, such as images.
 */
@Path("/media")
public class MediaResource
{
	private CosmosDBLayerUsers cosmosDBUser;
	private CosmosDBLayerHouse cosmosDBHouse;
	
	/** CONSTANTS **/

	private static final String UPLOAD_SUCCESS = "Image uploaded with success!!";
	private static final String DELETE_SUCCESS = "Image deleted with success!!";
	private static final String UPLOAD_HOUSE_PROBLEM = "Error uploading house photo";

	private static final String DELETE_HOUSE_PHOTO_PROBLEM = "Error uploading house photo";
	private static final String USER_AUTHENTICATION_PROBLEM = "User id doesnt match user token";
	private static final String SOMETHING_WRONG =  "Something went wrong";
	private static final String SOMETHING_WRONG_USER =  "something went wrong while trying to get the user";
	private static final String NOT_DELETED_USER_PHOTO =  "Couldn't delete the user photo";
	private static final String NOT_DELETED_HOUSE_PHOTO =  "Couldn't delete the house photo";



	private static final Logger LOG = Logger.getLogger(MediaResource.class.getName());

	public MediaResource() {
		this.cosmosDBUser = CosmosDBLayerUsers.getInstance();
		this.cosmosDBHouse = CosmosDBLayerHouse.getInstance();
	}

	Map<String,byte[]> map = new HashMap<>();

	String storageConnectionString = "DefaultEndpointsProtocol=https;AccountName=scc2324n60441;AccountKey=kvc+0wG0XeJp9IQ/HCKZU8QYcQ69ayoSqTQMcOCf6g/hMVbjGeqjBeO1snjAy1pHjVr1vFF6c9DC+ASt+ZDv6g==;EndpointSuffix=core.windows.net";

	// Get container client
	File imagesDirectory = new File("/mnt/vol");

	/**
	 * Post a new image.The id of the image is its hash.
	 */
	@POST
	@Path("/user/{userId}")
	@Consumes(MediaType.APPLICATION_OCTET_STREAM)
	@Produces(MediaType.APPLICATION_JSON)
	public Response uploadUserPhoto(@CookieParam("scc:session") Cookie session,
									@PathParam("userId") String userId, byte[] contents) {
		try {
			if(!checkCookieUserGetUserId(session).equals(userId)){
				return Response.status(Response.Status.FORBIDDEN).entity(USER_AUTHENTICATION_PROBLEM).build();
			}
		}
		catch( WebApplicationException e) { throw e; }
		catch( Exception e) { throw new InternalServerErrorException(e); }

		try {
			String filename = Hash.of(contents) + userId + ".png";

			String response = cosmosDBUser.putUserFilenamePhoto(filename, userId);

			if (response.equals("-1"))
				return Response.status(Response.Status.NOT_FOUND).entity(SOMETHING_WRONG_USER).build();
			if (!response.isEmpty()) {
				try {
					File oldFile = new File(imagesDirectory, response);
					oldFile.delete();
				} catch (Exception e) {
					LOG.severe("Não apagou a foto antiga..... : " + e.getMessage());
				}
			}

			File newFile = new File(imagesDirectory, filename);
			try (FileOutputStream fos = new FileOutputStream(newFile)) {
				fos.write(contents);
			} catch (Exception e) {
				LOG.severe(e.getMessage());
			}

			return Response.ok(UPLOAD_SUCCESS).build();

		} catch (Exception e) {
			LOG.severe(e.getMessage());
		}

		return Response.status(Response.Status.BAD_REQUEST).entity(SOMETHING_WRONG).build();
	}

	@DELETE
	@Path("/user/delete/{userId}")
	@Produces(MediaType.APPLICATION_JSON)
	public Response deleteUserPhoto(@CookieParam("scc:session") Cookie session,
									@PathParam("userId") String userId) {
		try {
			if(!checkCookieUserGetUserId(session).equals(userId)){
				return Response.status(Response.Status.FORBIDDEN).entity(USER_AUTHENTICATION_PROBLEM).build();
			}
		}
		catch( WebApplicationException e) { throw e; }
		catch( Exception e) { throw new InternalServerErrorException(e); }

		try {
			String response = cosmosDBUser.removeUserPhoto(userId);

			if(response.equals("-1"))
				return Response.status(Response.Status.NOT_FOUND).entity(SOMETHING_WRONG_USER).build();
			if(response.isEmpty()) {
				return Response.ok().build();
			}

			File oldFile = new File(imagesDirectory, response);
			boolean deleted = oldFile.delete();

			if(!deleted)
				return Response.status(Response.Status.NOT_MODIFIED).entity(NOT_DELETED_USER_PHOTO).build();

			return Response.ok(DELETE_SUCCESS).build();

		}catch( Exception e) {
			LOG.severe(e.getMessage());
		}

		return Response.status(Response.Status.BAD_REQUEST).entity(SOMETHING_WRONG).build();
	}

	/**
	 * Post a new image.The id of the image is its hash.
	 */
	@POST
	@Path("/house/{houseId}")
	@Consumes(MediaType.APPLICATION_OCTET_STREAM)
	@Produces(MediaType.APPLICATION_JSON)
	public Response uploadHousePhoto(@CookieParam("scc:session") Cookie session,
									 @PathParam("houseId") String houseId, byte[] contents) {

		String userId = "";
		try {
			userId = checkCookieUserGetUserId(session);
		}
		catch( WebApplicationException e) { throw e; }
		catch( Exception e) { throw new InternalServerErrorException(e); }

		try {
			String filename = Hash.of(contents)+houseId+".png";

			Response addHousePhotoResponse = cosmosDBHouse.addHousePhoto(userId,filename,houseId);

			if(addHousePhotoResponse.getStatus()<300){
				File newFile = new File(imagesDirectory, filename);
				try (FileOutputStream fos = new FileOutputStream(newFile)) {
					fos.write(contents);
				} catch (Exception e) {
					LOG.severe(e.getMessage());
				}
				return addHousePhotoResponse;
			}
		} catch( Exception e) {
			LOG.severe(e.getMessage());
		}

		return Response.status(Response.Status.CONFLICT).entity(UPLOAD_HOUSE_PROBLEM).build();
	}

	@DELETE
	@Path("/house/delete/{houseId}")
	@Produces(MediaType.APPLICATION_JSON)
	public Response deleteHousePhoto(@CookieParam("scc:session") Cookie session,
									 @PathParam("houseId") String houseId,@QueryParam("filename") String filename) {

		String userId = "";
		try {
			userId = checkCookieUserGetUserId(session);
		}
		catch( WebApplicationException e) { throw e; }
		catch( Exception e) { throw new InternalServerErrorException(e); }

		try {
			String response = cosmosDBHouse.removeHousePhoto(userId,filename,houseId);

			if(response.equals("-1"))
				return Response.status(Response.Status.NOT_FOUND).entity(SOMETHING_WRONG_USER).build();
			if(response.isEmpty()) {
				return Response.ok().build();
			}

			File oldFile = new File(imagesDirectory, response);
			boolean deleted = oldFile.delete();

			if(!deleted)
				return Response.status(Response.Status.NOT_MODIFIED).entity(NOT_DELETED_HOUSE_PHOTO).build();

			return Response.ok(DELETE_SUCCESS).build();

		} catch( Exception e) {
			LOG.severe(e.getMessage());
		}

		return Response.status(Response.Status.CONFLICT).entity(DELETE_HOUSE_PHOTO_PROBLEM).build();
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


	/**
	 * Return the contents of an image. Throw an appropriate error message if
	 * id does not exist.
	 */
	@GET
	@Path("/{id}")
	@Produces(MediaType.APPLICATION_OCTET_STREAM)
	public byte[] download(@PathParam("id") String id) {
		try {
			File file = new File(imagesDirectory, id);

			if (file.exists()) {
				return Files.readAllBytes(file.toPath());
			}
		} catch( Exception e) {
			LOG.severe(e.getMessage());
		}
		return null;
	}


}
