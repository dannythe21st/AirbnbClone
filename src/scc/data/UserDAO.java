package scc.data;

/**
 * Represents a User, as stored in the database
 */
public class UserDAO {
	private String _rid;
	private String _ts;
	private String id;
	private String name;
	private String pwd;
	private String photoId;


	public UserDAO() {
	}


	public UserDAO(User u) {
		this.id = u.getId();
		this.name = u.getName();
		this.photoId = u.getPhotoId();
	}

	public UserDAO(String id, String name, String pwd, String photoId) {
		this.id = id;
		this.name = name;
		this.pwd = pwd;
		this.photoId = photoId;
	}
	public String getId() {
		return id;
	}
	public void setId(String id) {
		this.id = id;
	}
	public String getName() {
		return name;
	}
	public void setName(String name) {
		this.name = name;
	}
	public String getPwd() {
		return pwd;
	}
	public void setPwd(String pwd) {
		this.pwd = pwd;
	}
	public String getPhotoId() {
		return photoId;
	}
	public void setPhotoId(String photoId) {
		this.photoId = photoId;
	}


	public User toUser() {
		return new User( id, name, photoId);
	}

	@Override
	public String toString() {
		return "UserDAO{" +
				"_rid='" + _rid + '\'' +
				", _ts='" + _ts + '\'' +
				", id='" + id + '\'' +
				", name='" + name + '\'' +
				", pwd='" + pwd + '\'' +
				", photoId='" + photoId + '\'' +
				'}';
	}
}
