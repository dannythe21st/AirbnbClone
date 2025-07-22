
package scc.srv;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import redis.clients.jedis.Jedis;
import scc.cache.RedisCache;
import scc.data.HouseDAO;
import scc.data.QuestionDAO;
import scc.data.RentalDAO;
import scc.db.CosmosDBLayerHouse;
import scc.utils.Session;
import jakarta.ws.rs.core.Cookie;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.logging.Logger;

@Path("/house")
public class HouseResource {

    private CosmosDBLayerHouse cosmosDB;

    private CosmosDBLayerHouse cosmosDBRental;

    private static final Logger LOG = Logger.getLogger(HouseResource.class.getName());


    public HouseResource() {
        this.cosmosDB = CosmosDBLayerHouse.getInstance();
    }

    @DELETE
    @Path("/delete/{id}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response delHouseById(@CookieParam("scc:session") Cookie session,@PathParam("id") String houseId) {
        try {
            String userId = checkCookieUserGetUserId(session);
            return cosmosDB.delHouseById(userId,houseId);
        }
        catch( WebApplicationException e) { throw e; }
        catch( Exception e) { throw new InternalServerErrorException(e); }

    }


    @POST
    @Path("/create")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response addHouse(@CookieParam("scc:session") Cookie session,HouseDAO house) {
        try {
            String userId = checkCookieUserGetUserId(session);
            if(userId.equals(house.getOwnerId()))
                return cosmosDB.addHouse(house);
            return Response.status(Response.Status.FORBIDDEN).entity("User token and house owner don't match").build();
        }
        catch( WebApplicationException e) { throw e; }
        catch( Exception e) { throw new InternalServerErrorException(e); }
    }

    @PUT
    @Path("/update")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response updateHouse(@CookieParam("scc:session") Cookie session,HouseDAO house) {
        try {
            return cosmosDB.updateHouse(checkCookieUserGetUserId(session),house);
        }
        catch( WebApplicationException e) { throw e; }
        catch( Exception e) { throw new InternalServerErrorException(e); }

    }


    @PUT
    @Path("/toggleDiscount/{id}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response toggleDiscount(@CookieParam("scc:session") Cookie session,@PathParam("id") String houseId, @QueryParam("toggle") boolean discount) {
        try {
            return cosmosDB.toggleDiscount(checkCookieUserGetUserId(session),houseId, discount);
        }
        catch( WebApplicationException e) { throw e; }
        catch( Exception e) { throw new InternalServerErrorException(e); }

    }

    @GET
    @Path("/get/{id}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getHouseById(@CookieParam("scc:session") Cookie session,@PathParam("id") String houseId) {
        try {
            checkCookieUserGetUserId(session);
        }
        catch( WebApplicationException e) { throw e; }
        catch( Exception e) { throw new InternalServerErrorException(e); }

        return cosmosDB.getHouseById(houseId);

    }

    @GET
    @Path("/list")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getHouses(@CookieParam("scc:session") Cookie session,@QueryParam("page") int page,@QueryParam("elements") int elements) {
        try {
            checkCookieUserGetUserId(session);
        }
        catch( WebApplicationException e) { throw e; }
        catch( Exception e) { throw new InternalServerErrorException(e); }

        return cosmosDB.getHouses(page,elements);
    }

    @GET
    @Path("/user/{userId}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getHousesByUser(@CookieParam("scc:session") Cookie session,@PathParam("userId") String userId,@QueryParam("page") int page,@QueryParam("elements") int elements) {
        try {
            checkCookieUserGetUserId(session);
        }
        catch( WebApplicationException e) { throw e; }
        catch( Exception e) { throw new InternalServerErrorException(e); }

        return cosmosDB.getHousesByUser(userId,page,elements);
    }

    @GET
    @Path("/location/{location}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getHousesByLocation(@CookieParam("scc:session") Cookie session,
                                        @PathParam("location") String location,@QueryParam("page") int page,@QueryParam("elements") int elements) {
        try {
            checkCookieUserGetUserId(session);
        }
        catch( WebApplicationException e) { throw e; }
        catch( Exception e) { throw new InternalServerErrorException(e); }

        return cosmosDB.getHousesByLocation(location,page,elements);
    }


    @GET
    @Path("/discounts")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getNextDiscountedHouses(@CookieParam("scc:session") Cookie session,
                                            @QueryParam("page") int page,@QueryParam("elements") int elements){
        try {
            checkCookieUserGetUserId(session);
        }
        catch( WebApplicationException e) { throw e; }
        catch( Exception e) { throw new InternalServerErrorException(e); }

        return cosmosDB.getNextDiscountedHouses(page,elements);
    }


    /** QUESTION **/

    @GET
    @Path("/{id}/questions")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getQuestions(@CookieParam("scc:session") Cookie session,
                                 @PathParam("id") String houseId,@QueryParam("page") int page,@QueryParam("elements") int elements) {
        try {
            checkCookieUserGetUserId(session);
        }
        catch( WebApplicationException e) { throw e; }
        catch( Exception e) { throw new InternalServerErrorException(e); }

        return cosmosDB.getQuestions(houseId,page,elements);
    }
    @GET
    @Path("/question/{questionId}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getQuestionById(@CookieParam("scc:session") Cookie session,
                                    @PathParam("questionId") String questionId) {
        try {
            checkCookieUserGetUserId(session);
        }
        catch( WebApplicationException e) { throw e; }
        catch( Exception e) { throw new InternalServerErrorException(e); }
        return cosmosDB.getQuestionById(questionId);
    }

    @POST
    @Path("/question")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response addQuestion(@CookieParam("scc:session") Cookie session,
                                QuestionDAO question) {
        try {
            checkCookieUserGetUserId(session);
        }
        catch( WebApplicationException e) { throw e; }
        catch( Exception e) { throw new InternalServerErrorException(e); }

        return cosmosDB.addQuestion(question);
    }

    @POST
    @Path("/question/replyTo/{questionID}")
    @Consumes(MediaType.TEXT_PLAIN)
    @Produces(MediaType.APPLICATION_JSON)
    public Response replyQuestion(@CookieParam("scc:session") Cookie session,
                                  @PathParam("questionID") String questionID,String answer) {
        try {
            String ownerId = checkCookieUserGetUserId(session);
            return cosmosDB.replyToQuestion(ownerId,questionID,answer);
        }
        catch( WebApplicationException e) { throw e; }
        catch( Exception e) { throw new InternalServerErrorException(e); }
    }


    /** RENTAL **/

    @POST
    @Path("/rental")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response  createRental(@CookieParam("scc:session") Cookie session,RentalDAO rental){
        try {
            checkCookieUserGetUserId(session);
        }
        catch( WebApplicationException e) { throw e; }
        catch( Exception e) { throw new InternalServerErrorException(e); }

        return cosmosDB.createRental(rental);
    }

    @GET
    @Path("/{id}/listrental")
    @Produces(MediaType.APPLICATION_JSON)
    public Response listRental(@CookieParam("scc:session") Cookie session,
                               @QueryParam("page") int page,@QueryParam("elements") int elements,@PathParam("id") String houseId){
        try{
            return cosmosDB.listRental(checkCookieUserGetUserId(session),houseId,page,elements);
        }
        catch( WebApplicationException e) { throw e; }
        catch( Exception e) { throw new InternalServerErrorException(e); }

    }

    @GET
    @Path("/rental/{rentalId}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getRentalById(@CookieParam("scc:session") Cookie session,
                                  @PathParam("rentalId") String rentalId){
        try{
            return cosmosDB.getRentalById(checkCookieUserGetUserId(session),rentalId);
        }
        catch( WebApplicationException e) { throw e; }
        catch( Exception e) { throw new InternalServerErrorException(e); }

    }

    @GET
    @Path("/{id}/listmyrentals")
    @Produces(MediaType.APPLICATION_JSON)
    public Response listUserRentals(@CookieParam("scc:session") Cookie session, @PathParam("id") String userId,
                                    @QueryParam("past") boolean past,@QueryParam("next") boolean next,
                                    @QueryParam("page") int page,@QueryParam("elements") int elements){
        try{
            checkCookieUser(session, userId);
        }
        catch( WebApplicationException e) { throw e; }
        catch( Exception e) { throw new InternalServerErrorException(e); }

        return cosmosDB.listUserRentals(userId, past, next, page, elements);
    }

    @PUT
    @Path("/updaterental")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response updateRental(@CookieParam("scc:session") Cookie session,RentalDAO rental){
        try{
            return cosmosDB.updateRental(checkCookieUserGetUserId(session),rental);
        }
        catch( WebApplicationException e) { throw e; }
        catch( Exception e) { throw new InternalServerErrorException(e); }


    }


    @GET
    @Path("/available")
    @Produces(MediaType.APPLICATION_JSON)
    public Response searchForAvailableHousesByDateAndLocation(@CookieParam("scc:session") Cookie session,
                                                              @QueryParam("page") int page,@QueryParam("elements") int elements,
                                                              @QueryParam("inicialDate") String inicial_date,
                                                              @QueryParam("finalDate") String final_date,
                                                              @QueryParam("location") String location){
        try {
            checkCookieUserGetUserId(session);
        }
        catch( WebApplicationException e) { throw e; }
        catch( Exception e) { throw new InternalServerErrorException(e); }

        Date dateInicial;
        Date dateFinal;
        LOG.severe("Data inicial (1): "+inicial_date);
        LOG.severe("Data inicial (1): "+final_date);
        try {
            SimpleDateFormat formatoOriginal = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
            dateInicial = formatoOriginal.parse(inicial_date);
            dateFinal = formatoOriginal.parse(final_date);
            LOG.severe("Data inicial: "+dateInicial);
            LOG.severe("Data final: "+dateFinal);
            return cosmosDB.searchForAvailableHousesByDateAndLocation(dateInicial.getTime(),dateFinal.getTime(),
                    location,page,elements);
        }
        catch (Exception e) {
            LOG.severe(e.getMessage());
        }

        return Response.status(Response.Status.BAD_REQUEST).entity("invalid date format").build();


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

