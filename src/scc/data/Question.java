package scc.data;

/**
 * Represents a Question, as returned to the clients
 * 
 * NOTE: array of house ids is shown as an example of how to store a list of elements and 
 * handle the empty list.
 */
public class Question {
	private String id;
	private String title;
	private String description;
	private String houseId;
	private String askerId;

	public Question() {
	}

	public Question(String id, String title, String description, String houseId, String askerId) {
		this.id = id;
		this.title = title;
		this.description = description;
		this.houseId = houseId;
		this.askerId = askerId;
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

	@Override
	public String toString() {
		return "Question{" +
				"id='" + id + '\'' +
				", title='" + title + '\'' +
				", description='" + description + '\'' +
				", houseId='" + houseId + '\'' +
				", askerId='" + askerId + '\'' +
				'}';
	}
}
