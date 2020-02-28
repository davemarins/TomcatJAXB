package it.polito.dp2.BIB.sol3.db;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import it.polito.dp2.BIB.sol3.service.jaxb.Item;
import it.polito.dp2.BIB.sol3.service.jaxb.Items;
import it.polito.dp2.BIB.sol3.service.util.ResourseUtils;
import it.polito.dp2.BIB.sol3.service.jaxb.BookshelfType;
import it.polito.dp2.BIB.sol3.service.jaxb.BookshelvesType;

public class MyDB {

	private static int counter = 0;
	private static ConcurrentHashMap<Integer, BookshelfType> allBookshelves = new ConcurrentHashMap<>();
	private static ConcurrentHashMap<Integer, Integer> allReads = new ConcurrentHashMap<>();

	private MyDB() {
	}

	public static MyDB getMyDB() {
		return new MyDB();
	}

	synchronized public BookshelfType createBookshelf(String name) {
		// Cerco se il bookshelf è già stato creato
		List<BookshelfType> equalNameBookshelves = MyDB.allBookshelves.values().stream()
				.filter(b -> b.getName().equals(name)).collect(Collectors.toList());
		if (equalNameBookshelves.size() > 0)
			return equalNameBookshelves.get(0);
		BookshelfType bookshelf = new BookshelfType();
		bookshelf.setName(name);
		bookshelf.setId(BigInteger.valueOf(MyDB.counter));
		MyDB.allBookshelves.put(MyDB.counter, bookshelf);
		MyDB.allReads.put(MyDB.counter, 0);
		MyDB.counter++;
		return bookshelf;
	}

	synchronized public BookshelvesType searchBookshelves(String keyword) {
		BookshelvesType result = new BookshelvesType();
		if (MyDB.allBookshelves.values().size() > 0)
			result.getBookshelfType().addAll(MyDB.allBookshelves.values().stream()
					.filter(b -> b.getName().contains(keyword)).collect(Collectors.toList()));
		return result;
	}

	synchronized public BookshelfType getBookshelf(BigInteger id, boolean count) {
		BookshelfType result = MyDB.allBookshelves.get(id.intValue());
		if (result != null && MyDB.allReads.get(id.intValue()) != null) {
			if (count) {
				MyDB.allReads.put(id.intValue(), MyDB.allReads.get(id.intValue()) + 1);
			}
			return result;
		} else {
			return null;
		}
	}

	synchronized public Items getItemsInsideBookshelf(BigInteger bookshelfId, Items updatedItems) throws Exception {
		BookshelfType bookshelf = MyDB.allBookshelves.get(bookshelfId.intValue());
		if (bookshelf == null)
			return null;
		// 1st part - update the single bookshelf collection (not all of them)
		List<Item> itemsToBeRemoved = new ArrayList<>();
		for (Item ib : MyDB.allBookshelves.get(bookshelfId.intValue()).getItem()) {
			boolean found = false;
			for (Item i : updatedItems.getItem())
				if (ResourseUtils.areItemsEquals(i, ib)) {
					found = true;
					break;
				}
			if (!found)
				itemsToBeRemoved.add(ib);
		}
		itemsToBeRemoved.forEach(i -> MyDB.allBookshelves.get(bookshelfId.intValue()).getItem().remove(i));
		// 2nd part - return the result wanted
		Items result = new Items();
		bookshelf.getItem().forEach(bi -> result.getItem().add(bi));
		MyDB.allReads.put(bookshelfId.intValue(), MyDB.allReads.get(bookshelfId.intValue()) + 1);
		return result;
	}

	synchronized public int addItemToBookshelf(BigInteger bookshelfId, Item item) {
		BookshelfType bookshelf = MyDB.allBookshelves.get(bookshelfId.intValue());
		if (bookshelf == null) {
			return -2; // Bookshelf not found
		} else {
			// 422 Unprocessable Entity but no exception handler, so Forbidden
			if (bookshelf.getItem().size() >= 20)
				return -3; // Forbidden - Max size reached
			if (bookshelf.getItem().size() > 0) {
				try {
					bookshelf.getItem().stream().filter(i -> i.getSelf().equals(item.getSelf())).findFirst().get();
					return 0; // Item already present
				} catch (NoSuchElementException e) {
					// Nothing to do, Item not present
				}
			}
			bookshelf.getItem().add(item);
			return 0;
		}
	}

	synchronized public int removeItemFromBookshelf(BigInteger bookshelfId, Item item) {
		BookshelfType bookshelf = MyDB.allBookshelves.get(bookshelfId.intValue());
		if (bookshelf == null) {
			return -2; // Bookshelf not found
		} else {
			if (bookshelf.getItem().size() > 0) {
				try {
					Item result = bookshelf.getItem().stream().filter(i -> i.getSelf().equals(item.getSelf()))
							.findFirst().get();
					if (bookshelf.getItem().remove(result))
						return 0;
					else
						return -1; // Bad request - remove failed
				} catch (NoSuchElementException e) {
					return 0; // Item not found
				}
			} else
				return 0; // Nothing to remove
		}
	}

	synchronized public void removeItemFromAllBookshelf(Item item) {
		MyDB.allBookshelves.values().forEach(b -> b.getItem().remove(item));
	}

	synchronized public void updateItemFromAllBookshelf(Item newItem) {
		MyDB.allBookshelves.values().forEach(b -> {
			Item wanted = null;
			for (Item i : b.getItem()) {
				if (i.getSelf().equals(newItem.getSelf())) {
					wanted = i;
				}
			}
			if (wanted != null) {
				int index = b.getItem().indexOf(wanted);
				b.getItem().remove(wanted);
				b.getItem().add(index, newItem);
			}
		});
	}

	synchronized public boolean removeBookshelf(BigInteger bookshelfId) {
		if (MyDB.allBookshelves.get(bookshelfId.intValue()) != null
				&& MyDB.allReads.get(bookshelfId.intValue()) != null) {
			MyDB.allBookshelves.remove(bookshelfId.intValue());
			MyDB.allReads.remove(bookshelfId.intValue());
			return true;
		} else {
			return false;
		}
	}

	synchronized public int getReads(BigInteger bookshelfId) {
		if (MyDB.allBookshelves.get(bookshelfId.intValue()) != null
				&& MyDB.allReads.get(bookshelfId.intValue()) != null) {
			return MyDB.allReads.get(bookshelfId.intValue());
		} else {
			return -1;
		}
	}

}
