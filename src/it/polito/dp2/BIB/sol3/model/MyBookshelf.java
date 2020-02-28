package it.polito.dp2.BIB.sol3.model;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import it.polito.dp2.BIB.ass3.Bookshelf;
import it.polito.dp2.BIB.ass3.DestroyedBookshelfException;
import it.polito.dp2.BIB.ass3.ItemReader;
import it.polito.dp2.BIB.ass3.ServiceException;
import it.polito.dp2.BIB.ass3.TooManyItemsException;
import it.polito.dp2.BIB.ass3.UnknownItemException;
import it.polito.dp2.BIB.sol3.client.ItemReaderImpl;
import it.polito.dp2.BIB.sol3.service.jaxb.Item;
import it.polito.dp2.BIB.sol3.service.jaxb.Items;
import it.polito.dp2.BIB.sol3.service.util.ResourseUtils;

public class MyBookshelf implements Bookshelf {

	private String uri;
	private Client client;
	private WebTarget target;

	private boolean destroyed;
	private int id;
	private String name, self;
	private ConcurrentHashMap<ItemReader, BigInteger> itemsIds;

	public MyBookshelf(String name) {
		this.id = Integer.MIN_VALUE;
		this.destroyed = false;
		this.name = name;
		this.self = "";
		this.itemsIds = new ConcurrentHashMap<>(20);
		this.uri = System.getProperty("it.polito.dp2.BIB.ass3.URL");
		if (this.uri == null || this.uri.equals(""))
			this.uri = "http://localhost:8080/BiblioSystem/rest";
		this.client = ClientBuilder.newClient();
		this.target = client.target(uri).path("biblio");
	}

	// -------------------- START UTILS METHODS --------------------

	synchronized public void setId(int id) throws DestroyedBookshelfException {
		if (this.destroyed)
			throw new DestroyedBookshelfException();
		this.id = id;
	}

	synchronized public int getId() throws DestroyedBookshelfException {
		if (this.destroyed)
			throw new DestroyedBookshelfException();
		return this.id;
	}

	synchronized public void setSelf(String self) {
		this.self = self;
	}

	synchronized public String getSelf() {
		return this.self;
	}

	synchronized private void destroyBookshelfLocal() {
		this.id = Integer.MIN_VALUE;
		this.destroyed = true;
		this.name = "";
		this.self = "";
		this.itemsIds.clear();
		this.uri = "";
		this.client = null;
		this.target = null;
	}

	// -------------------- END UTILS METHODS --------------------

	@Override
	synchronized public String getName() throws DestroyedBookshelfException {
		if (this.destroyed)
			throw new DestroyedBookshelfException();
		return this.name;
	}

	synchronized public void addItemLocally(ItemReader item, BigInteger itemId)
			throws DestroyedBookshelfException, TooManyItemsException, UnknownItemException, ServiceException {
		if (this.destroyed)
			throw new DestroyedBookshelfException();
		if (this.itemsIds.size() >= 20)
			throw new TooManyItemsException();
		if (item == null)
			throw new UnknownItemException();
		this.itemsIds.put(item, itemId);
	}

	@Override
	synchronized public void addItem(ItemReader item)
			throws DestroyedBookshelfException, UnknownItemException, TooManyItemsException, ServiceException {
		if (this.destroyed)
			throw new DestroyedBookshelfException();
		if (this.itemsIds.size() >= 20)
			throw new TooManyItemsException();
		if (item == null)
			throw new UnknownItemException();
		if (item.getTitle() == null || item.getTitle().equals(""))
			throw new UnknownItemException();
		// API request for Id retrieve of the Item
		this.target = this.client.target(this.uri).path("biblio").path("items").queryParam("keyword", item.getTitle());
		Response reply = this.target.request().get();
		reply.bufferEntity();
		if (reply.getStatus() != 200)
			throw new ServiceException();
		Items items = reply.readEntity(Items.class);
		Item itemRequested = null;
		for (Item i : items.getItem())
			if (ResourseUtils.areItemAndItemReaderEqual(i, item))
				itemRequested = i;
		if (itemRequested == null)
			throw new UnknownItemException();
		BigInteger id = ResourseUtils.fromSelfToId(itemRequested.getSelf());
		// API request for adding an item
		this.target = this.client.target(this.uri).path("biblio").path("bookshelf").path(Integer.toString(this.id))
				.path("items");
		reply = this.target.request(MediaType.APPLICATION_JSON).put(Entity.xml(itemRequested));
		reply.bufferEntity();
		if (reply.getStatus() == 403) {
			throw new TooManyItemsException();
		} else if (reply.getStatus() == 204) {
			this.itemsIds.put(item, id);
		} else
			throw new ServiceException();
	}

	@Override
	synchronized public void removeItem(ItemReader item)
			throws DestroyedBookshelfException, UnknownItemException, ServiceException {
		if (this.destroyed)
			throw new DestroyedBookshelfException();
		if (item == null)
			throw new UnknownItemException();
		BigInteger id = this.itemsIds.get(item);
		this.target = this.client.target(this.uri).path("biblio").path("bookshelf").path(Integer.toString(this.id))
				.path("items").path(id.toString());
		Response reply = this.target.request().delete();
		reply.bufferEntity();
		if (reply.getStatus() == 404) {
			this.destroyBookshelfLocal();
			throw new DestroyedBookshelfException();
		} else if (reply.getStatus() == 204) {
			ItemReader key = null;
			for (ItemReader temp : this.itemsIds.keySet())
				if (temp.hashCode() == item.hashCode())
					key = temp;
			if (key == null)
				throw new UnknownItemException();
			this.itemsIds.remove(key);
		} else
			throw new ServiceException();
	}

	@Override
	synchronized public Set<ItemReader> getItems() throws DestroyedBookshelfException, ServiceException {
		if (this.destroyed)
			throw new DestroyedBookshelfException();
		this.target = this.client.target(this.uri).path("biblio").path("bookshelf").path(Integer.toString(this.id))
				.path("items");
		Response reply = this.target.request(MediaType.APPLICATION_JSON).get();
		reply.bufferEntity();
		if (reply.getStatus() == 404) {
			this.destroyBookshelfLocal();
			throw new DestroyedBookshelfException();
		} else if (reply.getStatus() == 200) {
			Items results = reply.readEntity(Items.class);

			// items to be removed

			List<ItemReader> itemReadersToBeRemoves = new ArrayList<>();
			for (ItemReader ir : this.itemsIds.keySet()) {
				boolean found = false;
				for (Item i : results.getItem())
					if (ResourseUtils.areItemAndItemReaderEqual(i, ir))
						found = true;
				if (!found)
					itemReadersToBeRemoves.add(ir);
			}
			itemReadersToBeRemoves.forEach(ir -> this.itemsIds.remove(ir));

			// items to be added
			
			for (Item i : results.getItem()) {
				boolean found = false;
				ItemReader newIr = new ItemReaderImpl(i);
				BigInteger newId = ResourseUtils.fromSelfToId(i.getSelf());
				for (ItemReader ir : this.itemsIds.keySet()) {
					if (ResourseUtils.areItemReadersEqual(ir, newIr)) {
						found = true;
						break;
					}
				}
				if (!found)
					this.itemsIds.put(newIr, newId);
			}

			return this.itemsIds.keySet();
		} else
			throw new ServiceException();
	}

	@Override
	synchronized public void destroyBookshelf() throws DestroyedBookshelfException, ServiceException {
		if (this.destroyed)
			throw new DestroyedBookshelfException();
		this.target = this.client.target(this.uri).path("biblio").path("bookshelf").path(Integer.toString(this.id));
		Response reply = this.target.request().delete();
		reply.bufferEntity();
		if (reply.getStatus() == 404) {
			this.destroyBookshelfLocal();
			throw new DestroyedBookshelfException();
		} else if (reply.getStatus() == 204) {
			this.destroyBookshelfLocal();
		} else
			throw new ServiceException();
	}

	@Override
	synchronized public int getNumberOfReads() throws DestroyedBookshelfException, ServiceException {
		if (this.destroyed)
			throw new DestroyedBookshelfException();
		this.target = this.client.target(this.uri).path("biblio").path("bookshelf").path(Integer.toString(this.id))
				.path("stats");
		Response reply = this.target.request(MediaType.TEXT_PLAIN).get();
		reply.bufferEntity();
		if (reply.getStatus() == 404) {
			this.destroyBookshelfLocal();
			throw new DestroyedBookshelfException();
		} else if (reply.getStatus() == 200) {
			return reply.readEntity(Integer.class);
		} else
			throw new ServiceException();
	}

}
