package app.freerouting.datastructures;

import app.freerouting.logger.FRLogger;

import java.io.Serializable;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Vector;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ConcurrentSkipListMap;

/**
 * Database of objects, for which Undo and Redo operations are made possible. The algorithm works
 * only for objects containing no references.
 */
public class UndoableObjects implements Serializable
{

  /**
   * The entries of this map are of type UndoableObject, the keys of type UndoableObjects.Storable.
   */
  private final ConcurrentMap<Storable, UndoableObjectNode> objects;
  /**
   * the lists of deleted objects on each undo level, which where already existing before the
   * previous snapshot.
   */
  private final Vector<Collection<UndoableObjectNode>> deleted_objects_stack;
  /**
   * the current undo level
   */
  private int stack_level;
  private boolean redo_possible = false;

  /**
   * Creates a new instance of UndoableObjectsList
   */
  public UndoableObjects()
  {
    stack_level = 0;
    objects = new ConcurrentSkipListMap<>();
    deleted_objects_stack = new Vector<>();
  }

  /**
   * Returns an iterator for sequential reading of the object list.
   *
   * @return an iterator for sequential reading of the object list
   */
  public Iterator<UndoableObjectNode> start_read_object()
  {
    return objects
        .values()
        .iterator();
  }

  /**
   * Reads the next object in this list. Returns null, if the list is exhausted. p_it must be
   * created by start_read_object.
   */
  public UndoableObjects.Storable read_object(Iterator<UndoableObjectNode> p_it)
  {
    while (p_it.hasNext())
    {
      UndoableObjectNode curr_node = p_it.next();
      // skip objects getting alive only by redo
      if (curr_node != null && curr_node.level <= this.stack_level)
      {
        return (curr_node.object);
      }
    }
    return null;
  }

  /**
   * Adds p_object to the UndoableObjectsList.
   */
  public void insert(UndoableObjects.Storable p_object)
  {
    disable_redo();
    UndoableObjectNode curr_undoable_object = new UndoableObjectNode(p_object, stack_level);
    objects.put(p_object, curr_undoable_object);
  }

  /**
   * Removes p_object from the top level of the UndoableObjectsList. Returns false, if p_object was
   * not found in the list.
   */
  public boolean delete(UndoableObjects.Storable p_object)
  {
    disable_redo();
    Collection<UndoableObjectNode> curr_delete_list;
    if (deleted_objects_stack.isEmpty())
    {
      // stack_level 0
      curr_delete_list = null;
    }
    else
    {
      curr_delete_list = deleted_objects_stack.lastElement();
    }
    // search p_object in the list
    UndoableObjectNode object_node = objects.get(p_object);
    if (object_node == null)
    {
      return false;
    }
    // if (object_node.object != p_object)
    { // p_object can be cloned from the object pointed by object_node.object
      // Since object_node.object has been retrieved via objects.get(p_object)
      // objects.remove(p_object) would certainly remove the object.
      // Thus ignore the warning and proceed with the deletion
      //
      // FRLogger.warn("UndoableObjectList.delete: Object inconsistent");
      // return false;
    }

    if (curr_delete_list != null)
    {
      if (object_node.level < this.stack_level)
      {
        // add curr_ob to the current delete list to make Undo possible.
        curr_delete_list.add(object_node);
      }
      else if (object_node.undo_object != null)
      {
        // add curr_ob.undo_object to the current delete list to make Undo possible.

        curr_delete_list.add(object_node.undo_object);
      }
    }
    objects.remove(p_object);
    return true;
  }

  /**
   * Makes the current state of the list restorable by Undo.
   */
  public void generate_snapshot()
  {
    disable_redo();
    Collection<UndoableObjectNode> curr_deleted_objects_list = new LinkedList<>();
    deleted_objects_stack.add(curr_deleted_objects_list);
    ++stack_level;
  }

  /**
   * Restores the situation before the last snapshot. Outputs the cancelled and the restored objects
   * (if != null) to enable the calling function to take additional actions needed for these
   * objects. Returns false, if no more undo is possible
   */
  public boolean undo(Collection<UndoableObjects.Storable> p_cancelled_objects, Collection<UndoableObjects.Storable> p_restored_objects)
  {
    if (stack_level == 0)
    {
      return false; // no more undo possible
    }
    for (UndoableObjectNode curr_node : objects.values())
    {
      if (curr_node.level == stack_level)
      {
        if (curr_node.undo_object != null)
        {
          // replace the current object by  its previous state.
          curr_node.undo_object.redo_object = curr_node;
          objects.put(curr_node.object, curr_node.undo_object);
          if (p_restored_objects != null)
          {
            p_restored_objects.add(curr_node.undo_object.object);
          }
        }
        if (p_cancelled_objects != null)
        {
          p_cancelled_objects.add(curr_node.object);
        }
      }
    }
    // restore the deleted objects
    Collection<UndoableObjectNode> curr_delete_list = deleted_objects_stack.elementAt(stack_level - 1);
    for (UndoableObjectNode curr_deleted_node : curr_delete_list)
    {
      this.objects.put(curr_deleted_node.object, curr_deleted_node);
      if (p_restored_objects != null)
      {
        p_restored_objects.add(curr_deleted_node.object);
      }
    }
    --this.stack_level;
    redo_possible = true;
    return true;
  }

  /**
   * Restores the situation before the last undo. Outputs the cancelled and the restored objects (if
   * != null) to enable the calling function to take additional actions needed for these objects.
   * Returns false, if no more redo is possible.
   */
  public boolean redo(Collection<UndoableObjects.Storable> p_cancelled_objects, Collection<UndoableObjects.Storable> p_restored_objects)
  {
    if (this.stack_level >= deleted_objects_stack.size())
    {
      return false; // already at the top level
    }
    ++this.stack_level;
    for (UndoableObjectNode curr_node : objects.values())
    {
      if (curr_node.redo_object != null && curr_node.redo_object.level == this.stack_level)
      {
        // Object was created on a lower level and changed on the current level,
        // replace the lower level object by the object on the current layer.
        objects.put(curr_node.object, curr_node.redo_object);
        if (p_cancelled_objects != null)
        {
          p_cancelled_objects.add(curr_node.object);
        }
        if (p_restored_objects != null)
        {
          p_restored_objects.add(curr_node.redo_object.object);
          // else the redo_object was deleted on the redo level
        }
      }
      else if (curr_node.level == this.stack_level)
      {
        // Object was created on the current level, allow it to be restored.
        p_restored_objects.add(curr_node.object);
      }
    }
    // Delete the objects, which were deleted on the current level, again.
    Collection<UndoableObjectNode> curr_delete_list = deleted_objects_stack.elementAt(stack_level - 1);
    for (UndoableObjectNode curr_deleted_node : curr_delete_list)
    {
      while (curr_deleted_node.redo_object != null && curr_deleted_node.redo_object.level <= this.stack_level)
      {
        curr_deleted_node = curr_deleted_node.redo_object;
      }
      if (this.objects.remove(curr_deleted_node.object) == null)
      {
        FRLogger.warn("previous deleted object not found");
      }
      if (p_restored_objects == null || !p_restored_objects.remove(curr_deleted_node.object))
      {
        // the object needs only be cancelled if it is already in the board
        if (p_cancelled_objects != null)
        {
          p_cancelled_objects.add(curr_deleted_node.object);
        }
      }
    }
    return true;
  }

  /**
   * Removes the top snapshot from the undo stack, so that its situation cannot be restored
   * anymore. Returns false, if no more snapshot could be popped.
   */
  public boolean pop_snapshot()
  {
    disable_redo();
    if (stack_level == 0)
    {
      return false;
    }
    for (UndoableObjectNode curr_node : objects.values())
    {
      if (curr_node.level == stack_level - 1)
      {
        if (curr_node.redo_object != null && curr_node.redo_object.level == stack_level)
        {
          curr_node.redo_object.undo_object = curr_node.undo_object;
          if (curr_node.undo_object != null)
          {
            curr_node.undo_object.redo_object = curr_node.redo_object;
          }
        }
      }
      else if (curr_node.level >= stack_level)
      {
        --curr_node.level;
      }
    }
    int deleted_objects_stack_size = deleted_objects_stack.size();
    if (deleted_objects_stack_size >= 2)
    {
      // join the top delete list with the delete list of the second top level
      Collection<UndoableObjectNode> from_delete_list = deleted_objects_stack.elementAt(deleted_objects_stack_size - 1);
      Collection<UndoableObjectNode> to_delete_list = deleted_objects_stack.elementAt(deleted_objects_stack_size - 2);
      for (UndoableObjectNode curr_deleted_node : from_delete_list)
      {
        if (curr_deleted_node.level < this.stack_level - 1)
        {
          to_delete_list.add(curr_deleted_node);
        }
        else if (curr_deleted_node.undo_object != null)
        {
          to_delete_list.add(curr_deleted_node.undo_object);
        }
      }
    }
    deleted_objects_stack.remove(deleted_objects_stack_size - 1);
    --stack_level;
    return true;
  }

  /**
   * Must be called before p_object will be modified after a snapshot for the first time, if it may
   * have existed before that snapshot.
   */
  public void save_for_undo(UndoableObjects.Storable p_object)
  {
    disable_redo();
    // search p_object in the map
    UndoableObjectNode curr_node = objects.get(p_object);
    if (curr_node == null)
    {
      FRLogger.warn("UndoableObjects.save_for_undo: object node not found");
      return;
    }
    if (curr_node.level < this.stack_level)
    {

      UndoableObjectNode old_node = new UndoableObjectNode((UndoableObjects.Storable) p_object.clone(), curr_node.level);
      old_node.undo_object = curr_node.undo_object;
      old_node.redo_object = curr_node;
      curr_node.undo_object = old_node;
      curr_node.level = this.stack_level;
    }
  }

  /**
   * Must be called, if objects are changed for the first time after undo.
   */
  private void disable_redo()
  {
    if (!redo_possible)
    {
      return;
    }
    redo_possible = false;
    // shorten the size of the deleted_objects_stack to this.stack_level
    deleted_objects_stack
        .subList(this.stack_level, deleted_objects_stack.size())
        .clear();
    Iterator<UndoableObjectNode> it = objects
        .values()
        .iterator();
    while (it.hasNext())
    {
      UndoableObjectNode curr_node = it.next();
      if (curr_node.level > this.stack_level)
      {
        it.remove();
      }
      else if (curr_node.level == this.stack_level)
      {
        curr_node.redo_object = null;
      }
    }
  }

  /**
   * Condition for an Object to be stored in an UndoableObjects database. An object of class
   * UndoableObjects.Storable must not contain any references.
   */
  public interface Storable extends Comparable<Object>
  {

    /**
     * Creates an exact copy of this object Public overwriting of the protected clone method in
     * java.lang.Object,
     */
    Object clone();
  }

  /**
   * Stores information for correct restoring or cancelling an object in an undo or redo operation.
   * p_level is the level in the Undo stack, where this object was inserted.
   */
  public static class UndoableObjectNode implements Serializable
  {

    final Storable object; // the object in the node
    int level; // the level in the Undo stack, where this node was inserted
    UndoableObjectNode undo_object; // the object to restore in an undo or null.
    UndoableObjectNode redo_object; // the object to restore in a redo or null.

    /**
     * Creates a new instance of UndoableObjectNode
     */
    UndoableObjectNode(Storable p_object, int p_level)
    {
      object = p_object;
      level = p_level;
      undo_object = null;
      redo_object = null;
    }
  }
}