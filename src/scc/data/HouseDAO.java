package scc.data;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

/**
 * Represents a House, as stored in the database
 */
public class HouseDAO {
	private String _rid;
	private String _ts;
	private String id;
	private String name;
	private String location;
	private String description;
	private List<String> photoIds;
	private double price;
	private double discount_price;
	private String ownerId;
	private boolean onDiscount;
	private boolean active;

	public HouseDAO() {
	}

	public HouseDAO(String id, String name, String location, String description, List<String> photoIds, double price,
					double discount_price, String ownerId, boolean onDiscount, boolean active) {
		this.id = id;
		this.name = name;
		this.location = location;
		this.description = description;
		this.photoIds = photoIds;
		this.price = price;
		this.discount_price = discount_price;
		this.ownerId = ownerId;
		this.onDiscount = onDiscount;
		this.active = active;
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

	public String getLocation() {
		return location;
	}

	public void setLocation(String location) {
		this.location = location;
	}

	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	public List<String> getPhotoIds() {
		return photoIds;
	}

	public void setPhotoIds(List<String> photoIds) {
		this.photoIds = photoIds;
	}

	public String getOwnerId() {
		return ownerId;
	}

	public void setOwnerId(String ownerId) {
		this.ownerId = ownerId;
	}

	public double getPrice() {
		return price;
	}

	public boolean isOnDiscount() {
		return onDiscount;
	}

	public void setOnDiscount(boolean onDiscount) {
		this.onDiscount = onDiscount;
	}

	public void setPrice(double price) {
		this.price = price;
	}

	public double getDiscount_price() {
		return discount_price;
	}

	public void setDiscount_price(double discount_price) {
		this.discount_price = discount_price;
	}

	public boolean isActive() {
		return active;
	}

	public void setActive(boolean active) {
		this.active = active;
	}

	public House toHouse() {
		return new House(id, name, location, description,  photoIds,  price,  discount_price,  ownerId,  onDiscount, active);
	}

	@Override
	public String toString() {
		return "HouseDAO{" +
				"id='" + id + '\'' +
				", name='" + name + '\'' +
				", location='" + location + '\'' +
				", description='" + description + '\'' +
				", photoIds=" + photoIds +
				", price=" + price +
				", discount_price=" + discount_price +
				", ownerId='" + ownerId + '\'' +
				", onDiscount=" + onDiscount +
				", active=" + active +
				'}';
	}

	public void addPhotoFilename(String filename) {
		//photoIds.add(filename);
        List<String> newList = new ArrayList<>(getPhotoIds());
		newList.add(filename);
		setPhotoIds(newList);
	}

	public boolean removePhotoFilename(String filename) {
		//photoIds.add(filename);
		List<String> newList = getPhotoIds();
		boolean exist = newList.remove(filename);
		setPhotoIds(newList);
		return exist;
	}
}
