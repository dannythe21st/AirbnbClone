package scc.data;

import java.util.Date;

/**
 * Represents a Question, as stored in the database
 */
public class QuestionDAO {
	private String _rid;
	private String _ts;
	private String id;
	private String title;
	private String description;
	private String houseId;
	private String askerId;
	private String reply;
	private Date creationDate;

	public QuestionDAO() {
	}

	public QuestionDAO(String id, String title, String description, String houseId, String askerId, Date creationDate) {
		this.id = id;
		this.title = title;
		this.description = description;
		this.houseId = houseId;
		this.askerId = askerId;
		this.reply = "";
		this.creationDate = creationDate;
	}

	public Date getCreationDate() {
		return creationDate;
	}

	public void setCreationDate(Date creationDate) {
		this.creationDate = creationDate;
	}

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public String getTitle() {
		return title;
	}

	public void setTitle(String title) {
		this.title = title;
	}

	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	public String getHouseId() {
		return houseId;
	}

	public void setHouseId(String houseId) {
		this.houseId = houseId;
	}

	public String getAskerId() {
		return askerId;
	}

	public void setAskerId(String askerId) {
		this.askerId = askerId;
	}

	public void setReply(String reply){

		this.reply=reply;
	}

	public String getReply(){
		return this.reply;
	}

	public boolean alreadyReplied(){
		return this.reply.equals("");
	}

	@Override
	public String toString() {
		return "QuestionDAO{" +
				"id='" + id + '\'' +
				", title='" + title + '\'' +
				", description='" + description + '\'' +
				", houseId='" + houseId + '\'' +
				", askerId='" + askerId + '\'' +
				", reply='" + reply + '\'' +
				", creationDate=" + creationDate +
				'}';
	}
}
