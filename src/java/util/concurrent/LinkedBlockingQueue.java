/*
 * ORACLE PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 */

/*
 *
 *
 *
 *
 *
 * Written by Doug Lea with assistance from members of JCP JSR-166
 * Expert Group and released to the public domain, as explained at
 * http://creativecommons.org/publicdomain/zero/1.0/
 */

package java.util.concurrent;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.AbstractQueue;
import java.util.Collection;
import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * An optionally-bounded {@linkplain BlockingQueue blocking queue} based on     //基于链表实现的一个阻塞队列
 * linked nodes.
 * This queue orders elements FIFO (first-in-first-out).                        //顺序是FIFO
 * The <em>head</em> of the queue is that element that has been on the          //入队时间最长的元素在头部
 * queue the longest time.                                                      //入队时间最短的在尾部,出队时是从头部获取的
 * The <em>tail</em> of the queue is that element that has been on the
 * queue the shortest time. New elements                                        //新元素入队插入到尾部
 * are inserted at the tail of the queue, and the queue retrieval               //出队从头部获取,所以LinkedBlockingQueue采用双向链表,可以双向检索.
 * operations obtain elements at the head of the queue.                         //头尾加了两把锁提高吞吐量.
 * Linked queues typically have higher throughput than array-based queues but   //链表结构比数组结构吞吐量高,为什么?
 * less predictable performance in most concurrent applications.                //但是在大多数的并发应用场景中较难预测,就是差异比较大.
 *
 * <p> The optional capacity bound constructor argument serves as a
 * way to prevent excessive queue expansion. The capacity, if unspecified,
 * is equal to {@link Integer#MAX_VALUE}.  Linked nodes are
 * dynamically created upon each insertion unless this would bring the          //默认阻塞队列容量为Integer#MAX_VALUE, 链表结构是动态添加元素的, 上限不允许超过Integer#MAX_VALUE
 * queue above capacity.
 *
 * <p>This class and its iterator implement all of the
 * <em>optional</em> methods of the {@link Collection} and {@link
 * Iterator} interfaces.
 *
 * <p>This class is a member of the
 * <a href="{@docRoot}/../technotes/guides/collections/index.html">
 * Java Collections Framework</a>.
 *
 * @since 1.5
 * @author Doug Lea
 * @param <E> the type of elements held in this collection
 *
 */
public class LinkedBlockingQueue<E> extends AbstractQueue<E>
        implements BlockingQueue<E>, java.io.Serializable {
    private static final long serialVersionUID = -6903933977591709194L;

    /*
     * A variant of the "two lock queue" algorithm.  The putLock gates      // 双锁队列算法的变体
     * entry to put (and offer), and has an associated condition for
     * waiting puts.  Similarly for the takeLock.  The "count" field
     * that they both rely on is maintained as an atomic to avoid           // count采用原子类,避免对两个锁同时加锁
     * needing to get both locks in most cases. Also, to minimize need      // 为了让put方法最少的获取takeLock, 采用了级联通知notifie
     * for puts to get takeLock and vice-versa, cascading notifies are      // 级联通知
     * used. When a put notices that it has enabled at least one take,      // 当put方法通知可以来获取元素了
     * it signals taker. That taker in turn signals others if more          // 获取操作take,获取了一个元素,如果还有可获取的会继续通知下一个.就这样级联下去.
     * items have been entered since the signal. And symmetrically for
     * takes signalling puts. Operations such as remove(Object) and         // remove等操作需要同时获取两把锁,禁止读写操作.
     * iterators acquire both locks.
     *
     * Visibility between writers and readers is provided as follows:       // 下面讲到读和写的可见性问题 (todo 怎么保证可见性呢?继续往下看)
     *                                                                      // 因为如果只有一把全局的锁,那读和写没有可见性问题,但是现在读和写是两把不同的锁,下面看下解释.
     * Whenever an element is enqueued, the putLock is acquired and         // 每次一个元素入队, 都需要获取putLock和更新count
     * count updated.  A subsequent reader guarantees visibility to the     // 随后的读线程为了保证可见性, 需要获取fullyLock或者获取takeLock
     * enqueued Node by either acquiring the putLock (via fullyLock)        // fullyLock方法用于一些批量操作,对全局加锁
     * or by acquiring the takeLock, and then reading n = count.get();      // 然后读取count.get()
     * this gives visibility to the first n items.                          // 这样items中前n个元素(first n)就保证可见性了, (todo 为什么呢?继续往下看)
     *                                                                      // 因为happen-before规定了一条可见性原则:volatile对象的写操作happen-before读操作,也就是写线程先写的操作对随后的读线程是可见的
     *                                                                      // volatile相当于一个内存屏障,volatile后面的指令不允许重排序到它之前
     *                                                                      // 这样写线程修改count-->读线程读取count-->读线程; 每次写对每次读操作都是有偏序关系的,所以前n个都是可见的.
     * To implement weakly consistent iterators, it appears we need to
     * keep all Nodes GC-reachable from a predecessor dequeued Node.
     * That would cause two problems:
     * - allow a rogue Iterator to cause unbounded memory retention
     * - cause cross-generational linking of old Nodes to new Nodes if
     *   a Node was tenured while live, which generational GCs have a
     *   hard time dealing with, causing repeated major collections.
     * However, only non-deleted Nodes need to be reachable from
     * dequeued Nodes, and reachability does not necessarily have to
     * be of the kind understood by the GC.  We use the trick of
     * linking a Node that has just been dequeued to itself.  Such a
     * self-link implicitly means to advance to head.next.
     */

    /**
     * Linked list node class
     */
    static class Node<E> {
        E item;

        /**
         * One of:
         * - the real successor Node
         * - this Node, meaning the successor is head.next
         * - null, meaning there is no successor (this is the last node)
         */
        Node<E> next;

        Node(E x) { item = x; }
    }

    /** The capacity bound, or Integer.MAX_VALUE if none */
    private final int capacity;

    /** Current number of elements */
    private final AtomicInteger count = new AtomicInteger(0);   //为什么要用原子类,因为LinkedBlockingQueue是线程安全的.修改的时候要考虑并发.

    /**
     * Head of linked list.
     * Invariant: head.item == null
     */
    private transient Node<E> head;     // 从head和last可知, LinkedBlockingQueue是一个双向链表.

    /**
     * Tail of linked list.
     * Invariant: last.next == null
     */
    private transient Node<E> last;

    /** Lock held by take, poll, etc */
    private final ReentrantLock takeLock = new ReentrantLock();     // takeLock 和 putLock是两把锁; 这是用的可重入锁, 默认是非公平的可重入锁

    /** Wait queue for waiting takes */
    private final Condition notEmpty = takeLock.newCondition();     // 这是takeLock对应的Condition

    /** Lock held by put, offer, etc */
    private final ReentrantLock putLock = new ReentrantLock();

    /** Wait queue for waiting puts */
    private final Condition notFull = putLock.newCondition();       // 这是putLock对应的condition

    /**
     * Signals a waiting take. Called only from put/offer (which do not
     * otherwise ordinarily lock takeLock.)
     */
    private void signalNotEmpty() {     // 唤醒,通知不为空了,可以来获取了
        final ReentrantLock takeLock = this.takeLock;
        takeLock.lock();
        try {
            notEmpty.signal();
        } finally {
            takeLock.unlock();
        }
    }

    /**
     * Signals a waiting put. Called only from take/poll.
     */
    private void signalNotFull() {      // 唤醒,通知不满了,可以往里面放了
        final ReentrantLock putLock = this.putLock;
        putLock.lock();
        try {
            notFull.signal();
        } finally {
            putLock.unlock();
        }
    }

    /**
     * Links node at end of queue.
     *
     * @param node the node
     */
    private void enqueue(Node<E> node) {    //入队操作
        // assert putLock.isHeldByCurrentThread();
        // assert last.next == null;
        last = last.next = node;
    }

    /**
     * Removes a node from head of queue.
     *
     * @return the node
     */
    private E dequeue() {       //出队操作
        // assert takeLock.isHeldByCurrentThread(); // 这块是作者自己注释掉的, 现在不需要判断是否是当前线程了,这个操作的外层已经做加锁动作了, 锁肯定是当前线程的.
        // assert head.item == null;
        Node<E> h = head;       //h指向头指针. (head是头指针,指向的是当前queue的头结点,后续的节点依次链接在一起,如果没有head的指向,节点都会被GC回收的)
        Node<E> first = h.next; //first指向h.next,也就是指向第二个.
        h.next = h; // help GC  //这个我理解为只是帮助GC回收: h.next指向自己,更容易被GC发现. (其实如果没有这一步,h引用指向的Node对象也会被回收的,下面有例子解释)
        head = first;           //head指向first,也就是第二个,那么h就没有引用指向他了, 那么h就会被GC了.
        E x = first.item;       //取出first里面的item值.
        first.item = null;      //first.item指向null, first现在也就是head是一个头节点(哑节点),head的下一个就是队列的第一个.
        return x;
    }

    /**
     * Lock to prevent both puts and takes.
     */
    void fullyLock() {
        putLock.lock();
        takeLock.lock();
    }

    /**
     * Unlock to allow both puts and takes.
     */
    void fullyUnlock() {
        takeLock.unlock();
        putLock.unlock();
    }

//     /**
//      * Tells whether both locks are held by current thread.
//      */
//     boolean isFullyLocked() {
//         return (putLock.isHeldByCurrentThread() &&
//                 takeLock.isHeldByCurrentThread());
//     }

    /**
     * Creates a {@code LinkedBlockingQueue} with a capacity of
     * {@link Integer#MAX_VALUE}.
     */
    public LinkedBlockingQueue() {
        this(Integer.MAX_VALUE);
    }   // 构造函数默认大小为Integer.MAX_VALUE

    /**
     * Creates a {@code LinkedBlockingQueue} with the given (fixed) capacity.
     *
     * @param capacity the capacity of this queue
     * @throws IllegalArgumentException if {@code capacity} is not greater
     *         than zero
     */
    public LinkedBlockingQueue(int capacity) {
        if (capacity <= 0) throw new IllegalArgumentException();
        this.capacity = capacity;
        last = head = new Node<E>(null);    //头指针和尾指针包装的item为null.
    }

    /**
     * Creates a {@code LinkedBlockingQueue} with a capacity of
     * {@link Integer#MAX_VALUE}, initially containing the elements of the
     * given collection,
     * added in traversal order of the collection's iterator.
     *
     * @param c the collection of elements to initially contain
     * @throws NullPointerException if the specified collection or any
     *         of its elements are null
     */
    public LinkedBlockingQueue(Collection<? extends E> c) {
        this(Integer.MAX_VALUE);
        final ReentrantLock putLock = this.putLock;
        putLock.lock(); // Never contended, but necessary for visibility    //这里并没有并发,只是为了用锁保证可见性.
        try {
            int n = 0;
            for (E e : c) {
                if (e == null)
                    throw new NullPointerException();
                if (n == capacity)
                    throw new IllegalStateException("Queue full");
                enqueue(new Node<E>(e));
                ++n;
            }
            count.set(n);
        } finally {
            putLock.unlock();
        }
    }


    // this doc comment is overridden to remove the reference to collections
    // greater in size than Integer.MAX_VALUE
    /**
     * Returns the number of elements in this queue.
     *
     * @return the number of elements in this queue
     */
    public int size() {
        return count.get();
    }

    // this doc comment is a modified copy of the inherited doc comment,
    // without the reference to unlimited queues.
    /**
     * Returns the number of additional elements that this queue can ideally
     * (in the absence of memory or resource constraints) accept without
     * blocking. This is always equal to the initial capacity of this queue
     * less the current {@code size} of this queue.
     *
     * <p>Note that you <em>cannot</em> always tell if an attempt to insert
     * an element will succeed by inspecting {@code remainingCapacity}
     * because it may be the case that another thread is about to
     * insert or remove an element.
     */
    public int remainingCapacity() {
        return capacity - count.get();
    }

    /**
     * Inserts the specified element at the tail of this queue, waiting if  //put方法,如果queue满了,会一直等待,直到有空间了线程被唤醒.
     * necessary for space to become available.
     *
     * @throws InterruptedException {@inheritDoc}
     * @throws NullPointerException {@inheritDoc}
     */
    public void put(E e) throws InterruptedException {
        if (e == null) throw new NullPointerException();
        // Note: convention in all put/take/etc is to preset local var  //预设置c为负数-1
        // holding count negative to indicate failure unless set.       //负数表示没有成功入队, 如果成功入队了,就不是-1了
        int c = -1;
        Node<E> node = new Node(e);
        final ReentrantLock putLock = this.putLock;
        final AtomicInteger count = this.count;
        putLock.lockInterruptibly();
        try {
            /*
             * Note that count is used in wait guard even though it is  //注意到count是用来监视是否需要等待队列非满的
             * not protected by lock. This works because count can      //count没有用锁保护,为神马呢(表面看起来,这里用到了putLock,但是count的自增和自减是分别在putLock和takeLock中的,并没有用同一把锁)
             * only decrease at this point (all other puts are shut     //todo, 其他的put操作都在等待获取putLock锁
             * out by lock), and we (or some other waiting put) are     //只要capacity改变,其他的put操作会接收到signal通知
             * signalled if it ever changes from capacity. Similarly
             * for all other uses of count in other wait guards.        //其他地方使用count也是这样使用的,可以看下其他地方count的使用
             */
            while (count.get() == capacity) {   //队列中节点数量=容量
                notFull.await();    //等待notFull通知才可以put
            }
            enqueue(node);          //node入队
            c = count.getAndIncrement();    //自增
            if (c + 1 < capacity)   //c+1就是当前的节点数量,如果小于capacity需要通知不满.
                notFull.signal();   //通知不满了, notFull是Condition,也必须在获得当前putLock的锁情况下才好调用.
        } finally {
            putLock.unlock();
        }
        if (c == 0)     //如果c==0,表示原来是空的,但是刚又放入了一个,现在是c+1了,所以需要通知非空. 注意到上面的注释,在任何改变count的地方,都要综合判断是否通知非满或非空!!
            signalNotEmpty();
    }

    /**
     * Inserts the specified element at the tail of this queue, waiting if  //offer这个操作是可以设置超时时间的操作.
     * necessary up to the specified wait time for space to become available.
     *
     * @return {@code true} if successful, or {@code false} if
     *         the specified waiting time elapses before space is available.
     * @throws InterruptedException {@inheritDoc}
     * @throws NullPointerException {@inheritDoc}
     */
    public boolean offer(E e, long timeout, TimeUnit unit)
        throws InterruptedException {

        if (e == null) throw new NullPointerException();
        long nanos = unit.toNanos(timeout);
        int c = -1;
        final ReentrantLock putLock = this.putLock;
        final AtomicInteger count = this.count;
        putLock.lockInterruptibly();
        try {
            while (count.get() == capacity) {
                if (nanos <= 0)
                    return false;
                nanos = notFull.awaitNanos(nanos);
            }
            enqueue(new Node<E>(e));
            c = count.getAndIncrement();
            if (c + 1 < capacity)
                notFull.signal();
        } finally {
            putLock.unlock();
        }
        if (c == 0)
            signalNotEmpty();
        return true;
    }

    /**
     * Inserts the specified element at the tail of this queue if it is
     * possible to do so immediately without exceeding the queue's capacity,
     * returning {@code true} upon success and {@code false} if this queue  //不等待,立马返回是否入队成功.
     * is full.
     * When using a capacity-restricted queue, this method is generally
     * preferable to method {@link BlockingQueue#add add}, which can fail to
     * insert an element only by throwing an exception.
     *
     * @throws NullPointerException if the specified element is null
     */
    public boolean offer(E e) {
        if (e == null) throw new NullPointerException();
        final AtomicInteger count = this.count;
        if (count.get() == capacity)
            return false;
        int c = -1;
        Node<E> node = new Node(e);
        final ReentrantLock putLock = this.putLock;
        putLock.lock();
        try {
            if (count.get() < capacity) {
                enqueue(node);
                c = count.getAndIncrement();
                if (c + 1 < capacity)
                    notFull.signal();
            }
        } finally {
            putLock.unlock();
        }
        if (c == 0)
            signalNotEmpty();
        return c >= 0;
    }

    // take方法,如果没有可获取的,会一直等待,直到queue有可获取的对象,然后线程被唤醒.
    // 并且这个方法会响应中断的
    public E take() throws InterruptedException {
        E x;
        int c = -1;
        final AtomicInteger count = this.count;
        final ReentrantLock takeLock = this.takeLock;
        takeLock.lockInterruptibly();
        try {
            while (count.get() == 0) {
                notEmpty.await();
            }
            x = dequeue();  //出队操作
            c = count.getAndDecrement();    //count减一
            if (c > 1)
                notEmpty.signal();
        } finally {
            takeLock.unlock();
        }
        if (c == capacity)  //这里如果原来是满的,现在需要通知非满
            signalNotFull();
        return x;
    }
    //poll方法可以设置超时时间
    public E poll(long timeout, TimeUnit unit) throws InterruptedException {
        E x = null;
        int c = -1;
        long nanos = unit.toNanos(timeout);
        final AtomicInteger count = this.count;
        final ReentrantLock takeLock = this.takeLock;
        takeLock.lockInterruptibly();
        try {
            while (count.get() == 0) {
                if (nanos <= 0)
                    return null;
                nanos = notEmpty.awaitNanos(nanos); //Condition的awaitNanos, 比Object的wait方法更强大, 可以设置超时时间, 其中实现是依赖AQS实现的.
            }
            x = dequeue();
            c = count.getAndDecrement();
            if (c > 1)
                notEmpty.signal();
        } finally {
            takeLock.unlock();
        }
        if (c == capacity)
            signalNotFull();
        return x;
    }
    //立马返回结果,如果没有值就返回null
    public E poll() {
        final AtomicInteger count = this.count;
        if (count.get() == 0)   //做个简单校验,没有值立马返回null
            return null;
        E x = null;             //预先设置为null
        int c = -1;
        final ReentrantLock takeLock = this.takeLock;
        takeLock.lock();        //这里要竞争锁,入过只有一个对象,先获取到锁的线程能获取到对象,另外一个线程只能返回null
        try {                   //LinkedBlockingQueue是一个线程安全的高吞吐量的阻塞队列.所有方法都是线程安全的.
            if (count.get() > 0) {
                x = dequeue();
                c = count.getAndDecrement();
                if (c > 1)      //这里poll虽然是获取动作,但是如果c>1,也需要通知非空; 入队和出队操作都要通知非空或非满; 这样对应的等待线程会被唤醒.
                    notEmpty.signal();  //唤醒一个线程,被唤醒的线程还是要竞争锁的
            }
        } finally {
            takeLock.unlock();
        }
        if (c == capacity)
            signalNotFull();
        return x;
    }
    //peek方法只是获取第一个,但是没有出队动作
    public E peek() {
        if (count.get() == 0)
            return null;
        final ReentrantLock takeLock = this.takeLock;
        takeLock.lock();
        try {
            Node<E> first = head.next;
            if (first == null)
                return null;
            else
                return first.item;
        } finally {
            takeLock.unlock();
        }
    }

    /**
     * Unlinks interior Node p with predecessor trail.
     */
    void unlink(Node<E> p, Node<E> trail) {
        // assert isFullyLocked();
        // p.next is not changed, to allow iterators that are
        // traversing p to maintain their weak-consistency guarantee.
        p.item = null;
        trail.next = p.next;
        if (last == p)
            last = trail;
        if (count.getAndDecrement() == capacity)
            notFull.signal();
    }

    /**
     * Removes a single instance of the specified element from this queue,
     * if it is present.  More formally, removes an element {@code e} such
     * that {@code o.equals(e)}, if this queue contains one or more such
     * elements.
     * Returns {@code true} if this queue contained the specified element
     * (or equivalently, if this queue changed as a result of the call).
     *
     * @param o element to be removed from this queue, if present
     * @return {@code true} if this queue changed as a result of the call
     */
    public boolean remove(Object o) {   //正常用不到remove方法,queue正常只使用入队出队操作.
        if (o == null) return false;
        fullyLock();                    // 两个锁都锁上了,禁止进行入队出队操作.
        try {
            for (Node<E> trail = head, p = trail.next;
                 p != null;             // 按顺序一个一个检索,直到p==null
                 trail = p, p = p.next) {
                if (o.equals(p.item)) { //找到了,就删除掉
                    unlink(p, trail);   //删除操作是一个unlink方法,意思是p从LinkedList链路中解除.
                    return true;        //返回删除成功
                }
            }
            return false;
        } finally {
            fullyUnlock();
        }
    }

    /**
     * Returns {@code true} if this queue contains the specified element.
     * More formally, returns {@code true} if and only if this queue contains
     * at least one element {@code e} such that {@code o.equals(e)}.
     *
     * @param o object to be checked for containment in this queue
     * @return {@code true} if this queue contains the specified element
     */
    public boolean contains(Object o) {     //这种需要检索的操作都是对全局加锁的,很影响性能,要小心使用!
        if (o == null) return false;
        fullyLock();
        try {
            for (Node<E> p = head.next; p != null; p = p.next)
                if (o.equals(p.item))
                    return true;
            return false;
        } finally {
            fullyUnlock();
        }
    }

    /**
     * Returns an array containing all of the elements in this queue, in
     * proper sequence.
     *
     * <p>The returned array will be "safe" in that no references to it are
     * maintained by this queue.  (In other words, this method must allocate
     * a new array).  The caller is thus free to modify the returned array.
     *
     * <p>This method acts as bridge between array-based and collection-based
     * APIs.
     *
     * @return an array containing all of the elements in this queue
     */
    public Object[] toArray() {
        fullyLock();
        try {
            int size = count.get();
            Object[] a = new Object[size];
            int k = 0;
            for (Node<E> p = head.next; p != null; p = p.next)
                a[k++] = p.item;
            return a;
        } finally {
            fullyUnlock();
        }
    }

    /**
     * Returns an array containing all of the elements in this queue, in
     * proper sequence; the runtime type of the returned array is that of
     * the specified array.  If the queue fits in the specified array, it
     * is returned therein.  Otherwise, a new array is allocated with the
     * runtime type of the specified array and the size of this queue.
     *
     * <p>If this queue fits in the specified array with room to spare
     * (i.e., the array has more elements than this queue), the element in
     * the array immediately following the end of the queue is set to
     * {@code null}.
     *
     * <p>Like the {@link #toArray()} method, this method acts as bridge between
     * array-based and collection-based APIs.  Further, this method allows
     * precise control over the runtime type of the output array, and may,
     * under certain circumstances, be used to save allocation costs.
     *
     * <p>Suppose {@code x} is a queue known to contain only strings.
     * The following code can be used to dump the queue into a newly
     * allocated array of {@code String}:
     *
     * <pre>
     *     String[] y = x.toArray(new String[0]);</pre>
     *
     * Note that {@code toArray(new Object[0])} is identical in function to
     * {@code toArray()}.
     *
     * @param a the array into which the elements of the queue are to
     *          be stored, if it is big enough; otherwise, a new array of the
     *          same runtime type is allocated for this purpose
     * @return an array containing all of the elements in this queue
     * @throws ArrayStoreException if the runtime type of the specified array
     *         is not a supertype of the runtime type of every element in
     *         this queue
     * @throws NullPointerException if the specified array is null
     */
    @SuppressWarnings("unchecked")
    public <T> T[] toArray(T[] a) {
        fullyLock();
        try {
            int size = count.get();
            if (a.length < size)
                a = (T[])java.lang.reflect.Array.newInstance
                    (a.getClass().getComponentType(), size);

            int k = 0;
            for (Node<E> p = head.next; p != null; p = p.next)
                a[k++] = (T)p.item;
            if (a.length > k)
                a[k] = null;
            return a;
        } finally {
            fullyUnlock();
        }
    }

    public String toString() {
        fullyLock();
        try {
            Node<E> p = head.next;
            if (p == null)
                return "[]";

            StringBuilder sb = new StringBuilder();
            sb.append('[');
            for (;;) {
                E e = p.item;
                sb.append(e == this ? "(this Collection)" : e);
                p = p.next;
                if (p == null)
                    return sb.append(']').toString();
                sb.append(',').append(' ');
            }
        } finally {
            fullyUnlock();
        }
    }

    /**
     * Atomically removes all of the elements from this queue.
     * The queue will be empty after this call returns.
     */
    public void clear() {
        fullyLock();
        try {
            for (Node<E> p, h = head; (p = h.next) != null; h = p) {    //从head开始依次遍历
                h.next = h;     //让h.next指向自己
                p.item = null;  //item置空
            }
            head = last;    //head指向last,上面的对象就都没有引用了,依赖GC删除掉.
            // assert head.item == null && head.next == null;
            if (count.getAndSet(0) == capacity) //修改count为0
                notFull.signal();
        } finally {
            fullyUnlock();
        }
    }

    /**
     * @throws UnsupportedOperationException {@inheritDoc}
     * @throws ClassCastException            {@inheritDoc}
     * @throws NullPointerException          {@inheritDoc}
     * @throws IllegalArgumentException      {@inheritDoc}
     */
    public int drainTo(Collection<? super E> c) {
        return drainTo(c, Integer.MAX_VALUE);
    }

    /**
     * @throws UnsupportedOperationException {@inheritDoc}
     * @throws ClassCastException            {@inheritDoc}
     * @throws NullPointerException          {@inheritDoc}
     * @throws IllegalArgumentException      {@inheritDoc}
     */
    public int drainTo(Collection<? super E> c, int maxElements) {  //批量转移多个对象到一个collection中.
        if (c == null)
            throw new NullPointerException();
        if (c == this)
            throw new IllegalArgumentException();
        boolean signalNotFull = false;
        final ReentrantLock takeLock = this.takeLock;
        takeLock.lock();
        try {
            int n = Math.min(maxElements, count.get());
            // count.get provides visibility to first n Nodes
            Node<E> h = head;
            int i = 0;
            try {
                while (i < n) {
                    Node<E> p = h.next;
                    c.add(p.item);
                    p.item = null;
                    h.next = h;
                    h = p;
                    ++i;
                }
                return n;
            } finally {
                // Restore invariants even if c.add() threw
                if (i > 0) {
                    // assert h.item == null;
                    head = h;
                    signalNotFull = (count.getAndAdd(-i) == capacity);
                }
            }
        } finally {
            takeLock.unlock();
            if (signalNotFull)
                signalNotFull();
        }
    }

    /**
     * Returns an iterator over the elements in this queue in proper sequence.
     * The elements will be returned in order from first (head) to last (tail).
     *
     * <p>The returned iterator is a "weakly consistent" iterator that          //弱一致性, 这个是什么意思呢?
     * will never throw {@link java.util.ConcurrentModificationException        //不会抛出ConcurrentModificationException异常的
     * ConcurrentModificationException}, and guarantees to traverse
     * elements as they existed upon construction of the iterator, and          //也就是不会阻止遍历的时候对queue进行修改操作,可能会遍历到修改操作的结果.
     * may (but is not guaranteed to) reflect any modifications
     * subsequent to construction.
     *
     * @return an iterator over the elements in this queue in proper sequence
     */
    public Iterator<E> iterator() {
      return new Itr();
    }

    private class Itr implements Iterator<E> {
        /*
         * Basic weakly-consistent iterator.  At all times hold the next
         * item to hand out so that if hasNext() reports true, we will
         * still have it to return even if lost race with a take etc.
         */
        private Node<E> current;
        private Node<E> lastRet;
        private E currentElement;

        Itr() {
            fullyLock();
            try {
                current = head.next;
                if (current != null)
                    currentElement = current.item;
            } finally {
                fullyUnlock();
            }
        }

        public boolean hasNext() {
            return current != null;
        }

        /**
         * Returns the next live successor of p, or null if no such.
         *
         * Unlike other traversal methods, iterators need to handle both:
         * - dequeued nodes (p.next == p)
         * - (possibly multiple) interior removed nodes (p.item == null)
         */
        private Node<E> nextNode(Node<E> p) {
            for (;;) {
                Node<E> s = p.next;
                if (s == p)
                    return head.next;
                if (s == null || s.item != null)
                    return s;
                p = s;
            }
        }

        public E next() {
            fullyLock();
            try {
                if (current == null)
                    throw new NoSuchElementException();
                E x = currentElement;
                lastRet = current;
                current = nextNode(current);
                currentElement = (current == null) ? null : current.item;
                return x;
            } finally {
                fullyUnlock();
            }
        }

        public void remove() {
            if (lastRet == null)
                throw new IllegalStateException();
            fullyLock();
            try {
                Node<E> node = lastRet;
                lastRet = null;
                for (Node<E> trail = head, p = trail.next;
                     p != null;
                     trail = p, p = p.next) {
                    if (p == node) {
                        unlink(p, trail);
                        break;
                    }
                }
            } finally {
                fullyUnlock();
            }
        }
    }

    /**
     * Save the state to a stream (that is, serialize it).
     *
     * @serialData The capacity is emitted (int), followed by all of
     * its elements (each an {@code Object}) in the proper order,
     * followed by a null
     * @param s the stream
     */
    private void writeObject(java.io.ObjectOutputStream s)
        throws java.io.IOException {

        fullyLock();
        try {
            // Write out any hidden stuff, plus capacity
            s.defaultWriteObject();

            // Write out all elements in the proper order.
            for (Node<E> p = head.next; p != null; p = p.next)
                s.writeObject(p.item);

            // Use trailing null as sentinel
            s.writeObject(null);
        } finally {
            fullyUnlock();
        }
    }

    /**
     * Reconstitute this queue instance from a stream (that is,
     * deserialize it).
     *
     * @param s the stream
     */
    private void readObject(java.io.ObjectInputStream s)
        throws java.io.IOException, ClassNotFoundException {
        // Read in capacity, and any hidden stuff
        s.defaultReadObject();

        count.set(0);
        last = head = new Node<E>(null);

        // Read in all elements and place in queue
        for (;;) {
            @SuppressWarnings("unchecked")
            E item = (E)s.readObject();
            if (item == null)
                break;
            add(item);
        }
    }
}
