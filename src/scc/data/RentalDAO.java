package scc.data;

import java.util.Date;

/**
 * Represents a rental, as stored in the database
 */
public class RentalDAO {
	private String _rid;
	private String _ts;
	private String id;
	private double price;
	private String houseId;
	private String renterId;

	private String location;
	private Date inicial_date;
	private Date final_date;

	public RentalDAO() {
	}

	public RentalDAO(String id, double price, String houseId, String renterId, String location, Date inicial_date, Date final_date) {
		this.id = id;
		this.price = price;
		this.houseId = houseId;
		this.renterId = renterId;
		this.location = location;
		this.inicial_date = inicial_date;
		this.final_date = final_date;
	}

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public double getPrice() {
		return price;
	}

	public void setPrice(double price) {
		this.price = price;
	}

	public String getHouseId() {
		return houseId;
	}

	public void setHouseId(String houseId) {
		this.houseId = houseId;
	}

	public String getRenterId() {
		return renterId;
	}

	public void setRenterId(String renterId) {
		this.renterId = renterId;
	}

	public Date getInicial_date() {
		return inicial_date;
	}

	public void setInicial_date(Date inicial_date) {
		this.inicial_date = inicial_date;
	}

	public Date getFinal_date() {
		return final_date;
	}

	public void setFinal_date(Date final_date) {
		this.final_date = final_date;
	}

	public String getLocation() {
		return location;
	}

	public void setLocation(String location) {
		this.location = location;
	}

	@Override
	public String toString() {
		return "RentalDAO{" +
				"id='" + id + '\'' +
				", price=" + price +
				", houseId='" + houseId + '\'' +
				", renterId='" + renterId + '\'' +
				", location='" + location + '\'' +
				", inicial_date=" + inicial_date +
				", final_date=" + final_date +
				'}';
	}
}
