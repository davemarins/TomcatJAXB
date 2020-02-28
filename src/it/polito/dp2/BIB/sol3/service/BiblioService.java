package it.polito.dp2.BIB.sol3.service;

import java.math.BigInteger;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;

import javax.ws.rs.core.UriInfo;

import it.polito.dp2.BIB.sol3.db.BadRequestInOperationException;
import it.polito.dp2.BIB.sol3.db.ConflictInOperationException;
import it.polito.dp2.BIB.sol3.db.DB;
import it.polito.dp2.BIB.sol3.db.ItemPage;
import it.polito.dp2.BIB.sol3.db.MyDB;
import it.polito.dp2.BIB.sol3.db.Neo4jDB;
import it.polito.dp2.BIB.sol3.service.jaxb.BookshelfType;
import it.polito.dp2.BIB.sol3.service.jaxb.BookshelvesType;
import it.polito.dp2.BIB.sol3.service.jaxb.Citation;
import it.polito.dp2.BIB.sol3.service.jaxb.Item;
import it.polito.dp2.BIB.sol3.service.jaxb.Items;
import it.polito.dp2.BIB.sol3.service.util.ResourseUtils;

public class BiblioService {

	private MyDB bookshelvesManager = MyDB.getMyDB();
	private DB n4jDb = Neo4jDB.getNeo4jDB();
	ResourseUtils rutil;

	public BiblioService(UriInfo uriInfo) {
		rutil = new ResourseUtils((uriInfo.getBaseUriBuilder()));
	}

	synchronized public Items getItems(SearchScope scope, String keyword, int beforeInclusive, int afterInclusive,
			BigInteger page) throws Exception {
		ItemPage itemPage = n4jDb.getItems(scope, keyword, beforeInclusive, afterInclusive, page);

		Items items = new Items();
		List<Item> list = items.getItem();

		Set<Entry<BigInteger, Item>> set = itemPage.getMap().entrySet();
		for (Entry<BigInteger, Item> entry : set) {
			Item item = entry.getValue();
			rutil.completeItem(item, entry.getKey());
			list.add(item);
		}
		items.setTotalPages(itemPage.getTotalPages());
		items.setPage(page);
		return items;
	}

	synchronized public Item getItem(BigInteger id) throws Exception {
		Item item = n4jDb.getItem(id);
		if (item != null)
			rutil.completeItem(item, id);
		return item;
	}

	synchronized public Item updateItem(BigInteger id, Item item) throws Exception {
		Item ret = n4jDb.updateItem(id, item);
		if (ret != null) {
			rutil.completeItem(item, id);
			// missing in the delivered Assignment
			this.bookshelvesManager.updateItemFromAllBookshelf(ret);
			return item;
		} else
			return null;
	}

	synchronized public Item createItem(Item item) throws Exception {
		BigInteger id = n4jDb.createItem(item);
		if (id == null)
			throw new Exception("Null id");
		rutil.completeItem(item, id);
		return item;
	}

	synchronized public BigInteger deleteItem(BigInteger id) throws ConflictServiceException, Exception {
		try {
			Item item = n4jDb.getItem(id);
			if (item == null)
				throw new Exception();
			BigInteger result = n4jDb.deleteItem(id);
			if (result == null || result.toString().equals(""))
				throw new Exception();
			this.bookshelvesManager.removeItemFromAllBookshelf(item);
			return result;
			// return n4jDb.deleteItem(id);
		} catch (ConflictInOperationException e) {
			throw new ConflictServiceException();
		}
	}

	synchronized public Citation createItemCitation(BigInteger id, BigInteger tid, Citation citation) throws Exception {
		try {
			return n4jDb.createItemCitation(id, tid, citation);
		} catch (BadRequestInOperationException e) {
			throw new BadRequestServiceException();
		}
	}

	synchronized public Citation getItemCitation(BigInteger id, BigInteger tid) throws Exception {
		Citation citation = n4jDb.getItemCitation(id, tid);
		if (citation != null)
			rutil.completeCitation(citation, id, tid);
		return citation;
	}

	synchronized public boolean deleteItemCitation(BigInteger id, BigInteger tid) throws Exception {
		return n4jDb.deleteItemCitation(id, tid);
	}

	synchronized public Items getItemCitations(BigInteger id) throws Exception {
		ItemPage itemPage = n4jDb.getItemCitations(id, BigInteger.ONE);
		if (itemPage == null)
			return null;

		Items items = new Items();
		List<Item> list = items.getItem();

		Set<Entry<BigInteger, Item>> set = itemPage.getMap().entrySet();
		for (Entry<BigInteger, Item> entry : set) {
			Item item = entry.getValue();
			rutil.completeItem(item, entry.getKey());
			list.add(item);
		}
		items.setTotalPages(itemPage.getTotalPages());
		items.setPage(BigInteger.ONE);
		return items;
	}

	synchronized public Items getItemCitedBy(BigInteger id) throws Exception {
		ItemPage itemPage = n4jDb.getItemCitedBy(id, BigInteger.ONE);
		if (itemPage == null)
			return null;

		Items items = new Items();
		List<Item> list = items.getItem();

		Set<Entry<BigInteger, Item>> set = itemPage.getMap().entrySet();
		for (Entry<BigInteger, Item> entry : set) {
			Item item = entry.getValue();
			rutil.completeItem(item, entry.getKey());
			list.add(item);
		}
		items.setTotalPages(itemPage.getTotalPages());
		items.setPage(BigInteger.ONE);
		return items;
	}

	// Bookshelves methods

	synchronized public BookshelfType createBookshelf(String name) throws Exception {
		BookshelfType bookshelf = this.bookshelvesManager.createBookshelf(name);
		if (bookshelf == null)
			throw new Exception("Null id");
		else {
			bookshelf.setSelf(rutil.fromIdToSelf(bookshelf.getId().intValue()));
			return bookshelf;
		}
	}

	synchronized public BookshelvesType searchBookshelf(String keyword) throws Exception {
		return this.bookshelvesManager.searchBookshelves(keyword);
	}

	synchronized public BookshelfType getBookshelf(BigInteger id, boolean count) throws Exception {
		BookshelfType bookshelf = this.bookshelvesManager.getBookshelf(id, count);
		if (bookshelf != null)
			bookshelf.setId(id);
		return bookshelf;
	}

	synchronized public Items getItemsInsideBookshelf(BigInteger bookshelfId) throws Exception {
		// when asking for all items, I need to refresh all of them in order to
		// check if an item has been deleted inside Neo4J without
		// using the client provided
		Items updatedItems = this.getItems(SearchScope.ALL, "", 10000, 0, BigInteger.ONE);
		return this.bookshelvesManager.getItemsInsideBookshelf(bookshelfId, updatedItems);
	}

	synchronized public int addItemToBookshelf(BigInteger bookshelfId, Item item) throws Exception {
		if (item == null) {
			System.out.println("L'item ritornato è nullo");
			return -1; // Bad request
		}
		Item test = this.getItem(ResourseUtils.fromSelfToId(item.getSelf()));
		if (test == null || !test.getSelf().equals(item.getSelf()))
			return -2; // Not found
		else
			return this.bookshelvesManager.addItemToBookshelf(bookshelfId, item);
	}

	synchronized public int removeItemFromBookshelf(BigInteger bookshelfId, BigInteger itemId) throws Exception {
		Item item = this.getItem(itemId);
		if (item == null) {
			return -2; // Not found
		} else {
			return this.bookshelvesManager.removeItemFromBookshelf(bookshelfId, item);
		}
	}

	synchronized public void removeItemFromAllBookshelf(BigInteger itemId) throws Exception {
		Item item = this.getItem(itemId);
		if (item == null)
			throw new Exception();
		this.bookshelvesManager.removeItemFromAllBookshelf(item);
	}

	synchronized public boolean removeBookshelf(BigInteger bookshelfId) throws Exception {
		if (this.getBookshelf(bookshelfId, false) != null) {
			return this.bookshelvesManager.removeBookshelf(bookshelfId);
		} else {
			return false; // Not found
		}
	}

	synchronized public int getTotalReadsOfBookshelf(BigInteger bookshelfId) throws Exception {
		if (this.getBookshelf(bookshelfId, false) != null) {
			return this.bookshelvesManager.getReads(bookshelfId);
		} else {
			return -1; // Not found
		}
	}

}
