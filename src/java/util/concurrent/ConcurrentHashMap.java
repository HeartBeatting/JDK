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
import java.util.concurrent.locks.*;
import java.util.*;
import java.io.Serializable;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.ObjectStreamField;

/**
 * A hash table supporting full concurrency of retrievals and               // 一个hash表,用于支持充分的并发检索
 * adjustable expected concurrency for updates. This class obeys the        // 支持修改期望的并发线程数
 * same functional specification as {@link java.util.Hashtable}, and        // 此类和HashTable遵循同样的功能
 * includes versions of methods corresponding to each method of             // 方法和HashTable的每个方法相匹配的
 * <tt>Hashtable</tt>. However, even though all operations are              // 尽管所有的操作都是线程安全的
 * thread-safe, retrieval operations do <em>not</em> entail locking,        // 检索操作并没有引起锁,就是检索操作不需要加锁.
 * and there is <em>not</em> any support for locking the entire table       // 所有方法,都没有对整个table加锁的操作
 * in a way that prevents all access.  This class is fully
 * interoperable with <tt>Hashtable</tt> in programs that rely on its       // 这个类和HashTable能相互替换,就是两个类的方法都是线程安全的
 * thread safety but not on its synchronization details.                    // 依赖于它的线程安全,但是不依赖于它的同步实现细节的.
 *                                                                          // 下面一段提到了弱一致性问题
 * <p> Retrieval operations (including <tt>get</tt>) generally do not       // 检索操作,包括get方法
 * block, so may overlap with update operations (including                  // 所以检索操作可能和更新操作重叠,包括了put和remove
 * <tt>put</tt> and <tt>remove</tt>). Retrievals reflect the results        // 检索到的结果 反映了最近完成的更新操作的结果
 * of the most recently <em>completed</em> update operations holding        // 注:get操作一定能看到已完成的put操作。已完成的put操作肯定在get读取count之前对count做了写入操作。如果put操作,没有对count写入,get是看不到结果的.
 * upon their onset.  For aggregate operations such as <tt>putAll</tt>      // 对聚合操作(批量操作),比如putAll和clear操作
 * and <tt>clear</tt>, concurrent retrievals may reflect insertion or       // 并发进行的检索操作可能体现部分插入或删除的结果
 * removal of only some entries.  Similarly, Iterators and                  // 类似的迭代器和枚举器
 * Enumerations return elements reflecting the state of the hash table      // 返回元素,体现了从枚举创建以来, hash table的状态
 * at some point at or since the creation of the iterator/enumeration.
 * They do <em>not</em> throw {@link ConcurrentModificationException}.      // 不会检测并抛出ConcurrentModificationException
 * However, iterators are designed to be used by only one thread at a time. // 枚举是被设计用来,同一时间只能一个线程使用的.
 *
 * <p> The allowed concurrency among update operations is guided by         // 更新操作的并发级别,受可选择的concurrencyLevel属性控制
 * the optional <tt>concurrencyLevel</tt> constructor argument              // 构造函数,concurrencyLevel默认为16
 * (default <tt>16</tt>), which is used as a hint for internal sizing.  The // 用于表示内部size大小
 * table is internally partitioned to try to permit the indicated           // table内部分区了,用来尽量允许指定数量的更新操作,并发进行没有竞争.
 * number of concurrent updates without contention. Because placement
 * in hash tables is essentially random, the actual concurrency will        // 因为元素在hash table的分布,本质上是随机的,实际的并发会有所变化
 * vary.  Ideally, you should choose a value to accommodate as many         // 理论上, 你应该选择一个值,适应尽量多的线程并发修改这个table (根据业务场景的并发量来设计)
 * threads as will ever concurrently modify the table. Using a
 * significantly higher value than you need can waste space and time,       // 使用明显大于需要的值会浪费空间和时间
 * and a significantly lower value can lead to thread contention. But       // 使用明显低的值会导致更多的线程竞争
 * overestimates and underestimates within an order of magnitude do         // 但是在一个数量级上,高估和低估一点,不会有太多明显的影响
 * not usually have much noticeable impact. A value of one is
 * appropriate when it is known that only one thread will modify and        // 当只有一个线程修改,其他所有线程只读时,值设置为1比较合适.
 * all others will only read. Also, resizing this or any other kind of      // 注意,修改值大小或者其他类型的hash table是一个相对缓慢的操作
 * hash table is a relatively slow operation, so, when possible, it is      // 要尽可能的预估好值,在构造方法中设置好.
 * a good idea to provide estimates of expected table sizes in
 * constructors.
 *
 * <p>This class and its views and iterators implement all of the
 * <em>optional</em> methods of the {@link Map} and {@link Iterator}        // 实现了Map和Iterator接口
 * interfaces.
 *
 * <p> Like {@link Hashtable} but unlike {@link HashMap}, this class        // 像HashTable,不像HashMap,这个类不允许空的key和空的value
 * does <em>not</em> allow <tt>null</tt> to be used as a key or value.
 *
 * <p>This class is a member of the
 * <a href="{@docRoot}/../technotes/guides/collections/index.html">
 * Java Collections Framework</a>.
 *
 * @since 1.5
 * @author Doug Lea
 * @param <K> the type of keys maintained by this map
 * @param <V> the type of mapped values
 */
public class ConcurrentHashMap<K, V> extends AbstractMap<K, V>
        implements ConcurrentMap<K, V>, Serializable {
    private static final long serialVersionUID = 7249069246763182397L;

    /*
     * The basic strategy is to subdivide the table among Segments,     // 基本的策略就是将table切分成多个部分(segment)
     * each of which itself is a concurrently readable hash table.  To  // 每个部分是一个可以并发读取的hash table
     * reduce footprint, all but one segments are constructed only      // 为了减少足迹,需要时才会创建segment
     * when first needed (see ensureSegment). To maintain visibility    // 为了维护可见性
     * in the presence of lazy construction, accesses to segments as    // 在懒构造方法中
     * well as elements of segment's table must use volatile access,    // 获取segments和segments 里table元素,必须使用volatile方式的访问
     * which is done via Unsafe within methods segmentAt etc            // 通过Unsafe类
     * below. These provide the functionality of AtomicReferenceArrays  // 这些提供了数组的原子引用
     * but reduce the levels of indirection. Additionally,              // 但是降低了直接性
     * volatile-writes of table elements and entry "next" fields        // 此外volatile变量的写操作和next属性域
     * within locked operations use the cheaper "lazySet" forms of      // 在锁操作内部,使用更为廉价的lazySet类型的写操作
     * writes (via putOrderedObject) because these writes are always    // 通过putOrderedObject操作
     * followed by lock releases that maintain sequential consistency   // 因为这些写操作,总是跟随着锁释放操作,维护对table修改操作的顺序一致性.
     * of table updates.
     *
     * Historical note: The previous version of this class relied       // 历史笔记: 此类的以前版本,对final属性域的依赖很重
     * heavily on "final" fields, which avoided some volatile reads at  // 用于避免大量的初始读入volatile变量
     * the expense of a large initial footprint.  Some remnants of      // 老的设计残留物
     * that design (including forced construction of segment 0) exist
     * to ensure serialization compatibility.                           // 确保序列化兼容性? 最后一段什么意思没看懂
     */

    /* ---------------- Constants -------------- */

    /**
     * The default initial capacity for this table,
     * used when not otherwise specified in a constructor.  // 构造函数没有指定时,取这个默认值.
     */
    static final int DEFAULT_INITIAL_CAPACITY = 16;     // 默认初始容量, 这个是每个segment的容量大小

    /**
     * The default load factor for this table, used when not
     * otherwise specified in a constructor.
     */
    static final float DEFAULT_LOAD_FACTOR = 0.75f;     // 默认系数. 每个segment扩容是根据这个参数的.

    /**
     * The default concurrency level for this table, used when not
     * otherwise specified in a constructor.
     */
    static final int DEFAULT_CONCURRENCY_LEVEL = 16;    // 默认并发级别, 其实就是默认创建的segment的个数.

    /**
     * The maximum capacity, used if a higher value is implicitly
     * specified by either of the constructors with arguments.  MUST
     * be a power of two <= 1<<30 to ensure that entries are indexable
     * using ints.
     */
    static final int MAXIMUM_CAPACITY = 1 << 30;    // 最大容量

    /**
     * The minimum capacity for per-segment tables.  Must be a power
     * of two, at least two to avoid immediate resizing on next use
     * after lazy construction.
     */
    static final int MIN_SEGMENT_TABLE_CAPACITY = 2;    // segment最小容量

    /**
     * The maximum number of segments to allow; used to bound
     * constructor arguments. Must be power of two less than 1 << 24.
     */
    static final int MAX_SEGMENTS = 1 << 16; // slightly conservative   // segment最多个数

    /**
     * Number of unsynchronized retries in size and containsValue   // 不加锁的重试次数, 用于size和containsValue方法
     * methods before resorting to locking. This is used to avoid
     * unbounded retries if tables undergo continuous modification  // 用于避免没有上限的重试
     * which would make it impossible to obtain an accurate result.
     */
    static final int RETRIES_BEFORE_LOCK = 2;

    /* ---------------- Fields -------------- */

    /**
     * holds values which can't be initialized until after VM is booted.    // 持有不能被初始化的值, 直到vm被启动后
     */
    private static class Holder {   // 这里双static的用法是可以借鉴的,就是启动的时候将变量进行初始化,并且不允许后续的修改

        /**
        * Enable alternative hashing of String keys?                            // 是否启用对字符串的可替代的hash
        *
        * <p>Unlike the other hash map implementations we do not implement a    // 不想其他的hash map实现,我们没有实现一个threshold
        * threshold for regulating whether alternative hashing is used for      // 用来调整是否hash, 用于String类型的key
        * String keys. Alternative hashing is either enabled for all instances  // 可修改的hash开关, 对所有的实例开放或者关闭
        * or disabled for all instances.
        */
        static final boolean ALTERNATIVE_HASHING;

        static {
            // Use the "threshold" system property even though our threshold    // 即使threshold操作是开或者关,都使用"threshold"系统属性,threshold阈值
            // behaviour is "ON" or "OFF".
            String altThreshold = java.security.AccessController.doPrivileged(
                new sun.security.action.GetPropertyAction(
                    "jdk.map.althashing.threshold"));                       // 获取jvm启动的属性

            int threshold;
            try {
                threshold = (null != altThreshold)          // altThreshold不为null,就解析成int值
                        ? Integer.parseInt(altThreshold)
                        : Integer.MAX_VALUE;                // 否则为MAX值,后面ALTERNATIVE_HASHING会赋值false

                // disable alternative hashing if -1
                if (threshold == -1) {
                    threshold = Integer.MAX_VALUE;
                }

                if (threshold < 0) {
                    throw new IllegalArgumentException("value must be positive integer.");
                }
            } catch(IllegalArgumentException failed) {
                throw new Error("Illegal value for 'jdk.map.althashing.threshold'", failed);
            }
            ALTERNATIVE_HASHING = threshold <= MAXIMUM_CAPACITY;
        }
    }

    /**
     * A randomizing value associated with this instance that is applied to
     * hash code of keys to make hash collisions harder to find.        // 一个随机数,用于减少hash碰撞
     */
    private transient final int hashSeed = randomHashSeed(this);

    private static int randomHashSeed(ConcurrentHashMap instance) {
        if (sun.misc.VM.isBooted() && Holder.ALTERNATIVE_HASHING) {
            return sun.misc.Hashing.randomHashSeed(instance);           // 返回一个随机的hash seed种子
        }

        return 0;
    }

    /**
     * Mask value for indexing into segments. The upper bits of a
     * key's hash code are used to choose the segment.
     */
    final int segmentMask;

    /**
     * Shift value for indexing within segments.
     */
    final int segmentShift;

    /**
     * The segments, each of which is a specialized hash table.
     */
    final Segment<K,V>[] segments;

    transient Set<K> keySet;
    transient Set<Map.Entry<K,V>> entrySet;
    transient Collection<V> values;

    /**
     * ConcurrentHashMap list entry. Note that this is never exported
     * out as a user-visible Map.Entry. // 包级私有的.
     */ // 可以看到除了value不是final的，其它值都是final的，这意味着不能从hash链的中间或尾部添加或删除节点，因为这需要修改next引用值，所有的节点的修改只能从头部开始。
    static final class HashEntry<K,V> {
        final int hash;
        final K key;
        volatile V value;               // value是volatile,这是很关键的
        volatile HashEntry<K,V> next;   // next也是volatile的,在jdk 1.6中这个是final的.相比较多个版本的ConcurrentHashMap的实现,能学到不少东西,
                                        // 可以看到jdk 是怎么一步一步做优化的.
        HashEntry(int hash, K key, V value, HashEntry<K,V> next) {
            this.hash = hash;
            this.key = key;
            this.value = value;
            this.next = next;
        }

        /**
         * Sets next field with volatile write semantics.  (See above
         * about use of putOrderedObject.)
         */
        final void setNext(HashEntry<K,V> n) {
            UNSAFE.putOrderedObject(this, nextOffset, n);
        }

        // Unsafe mechanics
        static final sun.misc.Unsafe UNSAFE;    // 这里Unsafe是作为静态变量植入的,后续的对象实例就都可以使用这个能力了
        static final long nextOffset;
        static {
            try {
                UNSAFE = sun.misc.Unsafe.getUnsafe();
                Class k = HashEntry.class;
                nextOffset = UNSAFE.objectFieldOffset
                    (k.getDeclaredField("next"));
            } catch (Exception e) {
                throw new Error(e);
            }
        }
    }

    /**
     * Gets the ith element of given table (if nonnull) with volatile
     * read semantics. Note: This is manually integrated into a few
     * performance-sensitive methods to reduce call overhead.
     */
    @SuppressWarnings("unchecked")
    static final <K,V> HashEntry<K,V> entryAt(HashEntry<K,V>[] tab, int i) {
        return (tab == null) ? null :
            (HashEntry<K,V>) UNSAFE.getObjectVolatile
            (tab, ((long)i << TSHIFT) + TBASE);
    }

    /**
     * Sets the ith element of given table, with volatile write
     * semantics. (See above about use of putOrderedObject.)
     */
    static final <K,V> void setEntryAt(HashEntry<K,V>[] tab, int i,
                                       HashEntry<K,V> e) {
        UNSAFE.putOrderedObject(tab, ((long)i << TSHIFT) + TBASE, e);
    }

    /**
     * Applies a supplemental hash function to a given hashCode, which
     * defends against poor quality hash functions.  This is critical
     * because ConcurrentHashMap uses power-of-two length hash tables,
     * that otherwise encounter collisions for hashCodes that do not
     * differ in lower or upper bits.
     */
    private int hash(Object k) {
        int h = hashSeed;

        if ((0 != h) && (k instanceof String)) {
            return sun.misc.Hashing.stringHash32((String) k);   // String对象会有个特殊的hash方法.
        }

        h ^= k.hashCode();

        // Spread bits to regularize both segment and index locations,
        // using variant of single-word Wang/Jenkins hash.
        h += (h <<  15) ^ 0xffffcd7d;
        h ^= (h >>> 10);
        h += (h <<   3);
        h ^= (h >>>  6);
        h += (h <<   2) + (h << 14);
        return h ^ (h >>> 16);
    }

    /**
     * Segments are specialized versions of hash tables.  This      // segment是一种特别版本的hash table
     * subclasses from ReentrantLock opportunistically, just to     // 设计为可重入锁的子类
     * simplify some locking and avoid separate construction.       // 用于简化一些锁的操作 和 避免分开执行构造函数.
     */
    static final class Segment<K,V> extends ReentrantLock implements Serializable {
        /*
         * Segments maintain a table of entry lists that are always     // segment维护了一个table 里面是entry list
         * kept in a consistent state, so can be read (via volatile     // 永远是一致性的状态, 所以可以被读 (通过volatile变量的读)
         * reads of segments and tables) without locking.  This         // 不需要锁
         * requires replicating nodes when necessary during table       // 在table扩容的时候, 需要复制node节点
         * resizing, so the old lists can be traversed by readers       // 所以老的list可以被reader线程继续执行读操作
         * still using old version of table.
         *
         * This class defines only mutative methods requiring locking.  // 这个类只定义了需要锁定的方法
         * Except as noted, the methods of this class perform the
         * per-segment versions of ConcurrentHashMap methods.  (Other   // 每个segment的ConcurrentHashMap版本
         * methods are integrated directly into ConcurrentHashMap       // 其他方法直接集成到ConcurrentHashMap的方法中
         * methods.) These mutative methods use a form of controlled    // 这些变化的方法,在遇到竞争时使用了一种可控的自旋
         * spinning on contention via methods scanAndLock and           // 通过方法scanAndLock 和 scanAndLockForPut
         * scanAndLockForPut. These intersperse tryLocks with           // 这些散布的尝试锁,定位节点
         * traversals to locate nodes.  The main benefit is to absorb   // 主要的好处是承担缓存miss
         * cache misses (which are very common for hash tables) while   // 在hash table中是非常常见的
         * obtaining locks so that traversal is faster once
         * acquired. We do not actually use the found nodes since they  // 我们事实上没有使用找到的node节点,
         * must be re-acquired under lock anyway to ensure sequential   // 因为他们必须在加锁下才能被重新获取
         * consistency of updates (and in any case may be undetectably  // 保证更新的顺序一致性, 但是不会被检测到, 就是短时间是不可加的, 延时异步实现的
         * stale), but they will normally be much faster to re-locate.  // 他们常常会更快
         * Also, scanAndLockForPut speculatively creates a fresh node   // scanAndLockForPut操作投机的创建了一个新的node节点
         * to use in put if no node is found.                           // 没有找到node节点时,放一个进去
         */

        private static final long serialVersionUID = 2249069246763182397L;

        /**
         * The maximum number of times to tryLock in a prescan before   // 在提前扫描中,最大的tryLock次数
         * possibly blocking on acquire in preparation for a locked     // 在可能在阻塞获取segment锁之前
         * segment operation. On multiprocessors, using a bounded       // 在多核处理器,使用一个有上限的重试次数
         * number of retries maintains cache acquired while locating    // 维护缓存定位node
         * nodes.
         */
        static final int MAX_SCAN_RETRIES =
            Runtime.getRuntime().availableProcessors() > 1 ? 64 : 1;    // 不是单核的系统,就最大重试64次

        /**
         * The per-segment table. Elements are accessed via
         * entryAt/setEntryAt providing volatile semantics.
         */
        transient volatile HashEntry<K,V>[] table;                      // HashEntry数组, 每个Segment其实就是一个Hash表,每个Hash表内部维护的都是一个数组.

        /**
         * The number of elements. Accessed only either within locks
         * or among other volatile reads that maintain visibility.
         */
        transient int count;

        /**
         * The total number of mutative operations in this segment.     // 在segment中变化操作次数
         * Even though this may overflows 32 bits, it provides
         * sufficient accuracy for stability checks in CHM isEmpty()    // 它提供了充分的准确度,用于稳定的校验isEmpty()和size()方法
         * and size() methods.  Accessed only either within locks or    // 只会在锁中或者在volatile读取中, 保证可见性
         * among other volatile reads that maintain visibility.
         */
        transient int modCount;

        /**
         * The table is rehashed when its size exceeds this threshold.
         * (The value of this field is always <tt>(int)(capacity *
         * loadFactor)</tt>.)
         */
        transient int threshold;

        /**
         * The load factor for the hash table.  Even though this value
         * is same for all segments, it is replicated to avoid needing
         * links to outer object.
         * @serial
         */
        final float loadFactor;

        Segment(float lf, int threshold, HashEntry<K,V>[] tab) {
            this.loadFactor = lf;
            this.threshold = threshold;
            this.table = tab;
        }

        final V put(K key, int hash, V value, boolean onlyIfAbsent) {   // onlyIfAbsent为true,则只在可以不存在时才放入.
            HashEntry<K,V> node = tryLock() ? null :        // tryLock调用AQS的锁方法,尝试获取锁,获取失败了,调用scanAndLockForPut方法.
                scanAndLockForPut(key, hash, value);        // scanAndLockForPut加锁成功,也可能返回null的.
            V oldValue;
            try {
                HashEntry<K,V>[] tab = table;               // tab指向当前的table,table就是当前的HashEntry.
                int index = (tab.length - 1) & hash;        // index根据hash值计算出来的在segment中的哪个位置.
                HashEntry<K,V> first = entryAt(tab, index); // 根据index,找到在segment中哪个位置.
                for (HashEntry<K,V> e = first;;) {
                    if (e != null) {                        // e不为null,表示hash对应的桶是存在的
                        K k;
                        if ((k = e.key) == key ||
                            (e.hash == hash && key.equals(k))) {    // 一直找到key匹配的值
                            oldValue = e.value;             // 找到了,给oldValue赋值老的值
                            if (!onlyIfAbsent) {            // 这里是用来给putIfAbsent操作的
                                e.value = value;
                                ++modCount;                 // put操作会对modCount加一, 很重要的一点就是,modCount的操作都会加锁.
                            }
                            break;
                        }
                        e = e.next;                         // 往后寻找
                    }
                    else {                                  // e为null,表示key没有找到匹配的桶.桶不存在
                        if (node != null)                   // node不为null
                            node.setNext(first);    // node // 设置node的next,指向first; 这一步和下面的new HashEntry目的一样,都是加入到链表的头部
                        else                                // node为null,表示上面的scanAndLockForPut没有找到节点
                            node = new HashEntry<K,V>(hash, key, value, first); // 找到了node,这里就把node加入到entry链表的头部,next指向first
                        int c = count + 1;
                        if (c > threshold && tab.length < MAXIMUM_CAPACITY)
                            rehash(node);                   // 判断是否要再次hash. 这里要清楚ConcurrentMap的segment个数总是固定的,每个segment内部是可以再hash的
                        else
                            setEntryAt(tab, index, node);   // 这里是put操作,上面已经将node插入到first前面了,这里是将node放到index位置. 这时候node就是链表的头了.
                        ++modCount;                         // put操作会对modCount加一
                        count = c;
                        oldValue = null;                    // 没有找到老的值,结果就返回null
                        break;
                    }
                }
            } finally {                                     // 整个步骤都是加锁完成的,这里释放锁.
                unlock();                                   // 如果某个线程在循环尝试获取锁.这里释放锁了,那个线程就可以获取锁了.
            }
            return oldValue;
        }

        /**
         * Doubles size of table and repacks entries, also adding the       // 倍增table的size, 重新打包entry
         * given node to new table                                          // 把指定的node添加到新的table中
         */
        @SuppressWarnings("unchecked")
        private void rehash(HashEntry<K,V> node) {                          // 对入参HashEntry进行重新hash
            /*
             * Reclassify nodes in each list to new table.  Because we
             * are using power-of-two expansion, the elements from
             * each bin must either stay at same index, or move with a
             * power of two offset. We eliminate unnecessary node           // 消除不必要的阶段创建
             * creation by catching cases where old nodes can be
             * reused because their next fields won't change.
             * Statistically, at the default threshold, only about
             * one-sixth of them need cloning when a table                  // 只有六分之一需要克隆
             * doubles. The nodes they replace will be garbage
             * collectable as soon as they are no longer referenced by
             * any reader thread that may be in the midst of
             * concurrently traversing table. Entry accesses use plain
             * array indexing because they are followed by volatile
             * table write.
             */
            HashEntry<K,V>[] oldTable = table;
            int oldCapacity = oldTable.length;
            int newCapacity = oldCapacity << 1;
            threshold = (int)(newCapacity * loadFactor);
            HashEntry<K,V>[] newTable =
                (HashEntry<K,V>[]) new HashEntry[newCapacity];
            int sizeMask = newCapacity - 1;
            for (int i = 0; i < oldCapacity ; i++) {
                HashEntry<K,V> e = oldTable[i];
                if (e != null) {
                    HashEntry<K,V> next = e.next;
                    int idx = e.hash & sizeMask;
                    if (next == null)   //  Single node on list
                        newTable[idx] = e;
                    else { // Reuse consecutive sequence at same slot
                        HashEntry<K,V> lastRun = e;
                        int lastIdx = idx;
                        for (HashEntry<K,V> last = next;
                             last != null;
                             last = last.next) {
                            int k = last.hash & sizeMask;
                            if (k != lastIdx) {
                                lastIdx = k;
                                lastRun = last;
                            }
                        }
                        newTable[lastIdx] = lastRun;
                        // Clone remaining nodes
                        for (HashEntry<K,V> p = e; p != lastRun; p = p.next) {
                            V v = p.value;
                            int h = p.hash;
                            int k = h & sizeMask;
                            HashEntry<K,V> n = newTable[k];
                            newTable[k] = new HashEntry<K,V>(h, p.key, v, n);
                        }
                    }
                }
            }
            int nodeIndex = node.hash & sizeMask; // add the new node
            node.setNext(newTable[nodeIndex]);
            newTable[nodeIndex] = node;
            table = newTable;
        }

        /**
         * Scans for a node containing given key while trying to        // 当尝试获取锁时, 扫描包含key的segment
         * acquire lock, creating and returning one if not found. Upon  // 没有找到就创建并返回一个
         * return, guarantees that lock is held. UNlike in most         // 一旦返回,保证锁被持有着
         * methods, calls to method equals are not screened: Since      // 不像大多数的方法,调用equals方法不会屏蔽
         * traversal speed doesn't matter, we might as well help warm   // 因为扫描的运行速度并不要紧
         * up the associated code and accesses as well.                 // 我们可能帮助预热关联的code和获取
         *                                                              // 这里其实是在扫描的过程中,顺序扫描,直到找到key对应的node
         * @return a new node if key not found, else null               // 如果没有找到会返回一个新的node (HashEntry),
         */
        private HashEntry<K,V> scanAndLockForPut(K key, int hash, V value) {
            HashEntry<K,V> first = entryForHash(this, hash);        // 根据hash,找到第一个entry
            HashEntry<K,V> e = first;                                   // e指向first
            HashEntry<K,V> node = null;                                 // 初始化node为null
            int retries = -1; // negative while locating node           // 在定位节点的时候是负数
            while (!tryLock()) {                                        // 循环尝试获取锁
                HashEntry<K,V> f; // to recheck first below
                if (retries < 0) {                                      // 小于0
                    if (e == null) {                                    // 判断是否存在
                        if (node == null) // speculatively create node  // 不存在就返回一个新建的节点,这里可能是一直找 ==> 这里表示最终没有找到就new一个,其实是帮助后面的操作,先创建一个节点.
                            node = new HashEntry<K,V>(hash, key, value, null);  // 初始化node,主键为key,值为value,next为null
                        retries = 0;                                    // 初始化一个node后,赋值0,下次就不会进入到retries < 0了
                    }
                    else if (key.equals(e.key))                         // 存在并且等于第一个, 则找到了key, retries赋值为0  ==> 这里表示最终找到了,下次就不用进来了
                        retries = 0;                                    // 反正这里就是一直找, 直到找到了key对应的HashEntry
                    else
                        e = e.next;                                     // 这里继续往后遍历,还会继续进入retries < 0的逻辑,   ==> 这里表示继续往后找
                }
                else if (++retries > MAX_SCAN_RETRIES) {                // 小于最大重试次数时,继续while循环,继续重试.
                    lock();                                             // 大于最大重试次数则,直接阻塞对segment加锁,竞争segment的锁
                    break;
                }
                else if ((retries & 1) == 0 &&
                         (f = entryForHash(this, hash)) != first) {
                    e = first = f; // re-traverse if entry changed
                    retries = -1;                                       // 设置为-1,下次循环就当成第一次处理
                }
            }
            return node;
        }

        /**
         * Scans for a node containing the given key while trying to    // 对于包含给定key的segment,在直接阻塞获取锁之前,尽量尝试循环尝试获取锁,这样就不用进入阻塞等待队列了
         * acquire lock for a remove or replace operation. Upon         // 线程就不用先被阻塞,然后再被唤醒的过程了. 并发包里很多地方都有这种优化吧?
         * return, guarantees that lock is held.  Note that we must     // 方法返回时,保证lock锁仍然被持有着
         * lock even if the key is not found, to ensure sequential      // 即使key不存在,我们也要加锁.
         * consistency of updates.                                      // 为了保证修改的顺序一致性.
         */
        private void scanAndLock(Object key, int hash) {
            // similar to but simpler than scanAndLockForPut            // 和scanAndLockForPut类似,但是更简单
            HashEntry<K,V> first = entryForHash(this, hash);        // 根据hash值,找到segment里的entry; 这里的this是指当前的Segment
            HashEntry<K,V> e = first;
            int retries = -1;
            while (!tryLock()) {                                        // 循环尝试获取锁,只要获取成功,则结束循环,循环结束,说明获取锁成功了.
                HashEntry<K,V> f;
                if (retries < 0) {                                      // 第一次扫描
                    if (e == null || key.equals(e.key))                 // 为空或者第一个e就是目标key
                        retries = 0;                                    // 标记为不用重试了,这里其实就是减少不必要的重试
                    else
                        e = e.next;                                     // 否则指向下一个,while循环会继续重试
                }
                else if (++retries > MAX_SCAN_RETRIES) {                // 如果循环次数大于MAX_SCAN_RETRIES,这里会对retries先进行加一
                    lock();                                             // 直接整个segment加锁; 这个方法的好处就是在对segment加锁之前,会尽量重试多少次,如果
                    break;                                              // 跳出循环,方法结束了,这时候也已经获取锁成功了.
                }
                else if ((retries & 1) == 0 &&                          // 如果重试次数是偶数次 ,这里就是不是每次都校验嘛
                         (f = entryForHash(this, hash)) != first) { // 根据hash找到的entry不是first,这说明有新的元素加进来了
                    e = first = f;                                      // first指向f, e指向first
                    retries = -1;                                       // retries=-1,也就是entry链表结构变化了,重新开始了计算循环次数
                }
            }
        }

        /**
         * Remove; match on key only if value null, else match both.
         */
        final V remove(Object key, int hash, Object value) {
            if (!tryLock())
                scanAndLock(key, hash);
            V oldValue = null;
            try {
                HashEntry<K,V>[] tab = table;
                int index = (tab.length - 1) & hash;
                HashEntry<K,V> e = entryAt(tab, index);
                HashEntry<K,V> pred = null;
                while (e != null) {
                    K k;
                    HashEntry<K,V> next = e.next;               // next指向待删除节点的下一个.
                    if ((k = e.key) == key ||
                        (e.hash == hash && key.equals(k))) {    // 找到匹配的节点
                        V v = e.value;
                        if (value == null || value == v || value.equals(v)) {
                            if (pred == null)
                                setEntryAt(tab, index, next);
                            else
                                pred.setNext(next);
                            ++modCount;     // remove操作也会加一,modCount只是为了表示结构被修改了.
                            --count;
                            oldValue = v;
                        }
                        break;
                    }
                    pred = e;   // pred指向e
                    e = next;   // e指向next,继续遍历下一个
                }
            } finally {
                unlock();
            }
            return oldValue;
        }

        final boolean replace(K key, int hash, V oldValue, V newValue) {    // 如果老的值为oldValue,就替换为newValue
            if (!tryLock())
                scanAndLock(key, hash);
            boolean replaced = false;
            try {
                HashEntry<K,V> e;
                for (e = entryForHash(this, hash); e != null; e = e.next) {
                    K k;
                    if ((k = e.key) == key ||
                        (e.hash == hash && key.equals(k))) {
                        if (oldValue.equals(e.value)) {
                            e.value = newValue;
                            ++modCount;         // replace操作也会加一?
                            replaced = true;
                        }
                        break;
                    }
                }
            } finally {
                unlock();
            }
            return replaced;
        }

        final V replace(K key, int hash, V value) {
            if (!tryLock())             // 先尝试加锁
                scanAndLock(key, hash); // 加锁失败,就调用scanAndLock方法
            V oldValue = null;
            try {
                HashEntry<K,V> e;
                for (e = entryForHash(this, hash); e != null; e = e.next) { // entryForHash是根据hash值,找到segment里对应的entry
                    K k;                                                        // 这里的for循环是遍历entry链表
                    if ((k = e.key) == key ||
                        (e.hash == hash && key.equals(k))) {    // 找到同等的key或者hash值相等并切equals相等
                        oldValue = e.value;                     // oldValue修改成找到的value
                        e.value = value;                        // 替换e.value为入参的value
                        ++modCount;
                        break;                                  // 找到了就跳出循环
                    }
                }
            } finally {
                unlock();
            }
            return oldValue;                                    // 替换值以后,如果没有找到返回null,找到就返回原来的值. 这个和常见的map操作有同样的语义!
        }

        final void clear() {                            // clear是批量操作,加锁了,保证同一时间只有一个线程在修改.
            lock();                                     // 全局锁
            try {
                HashEntry<K,V>[] tab = table;
                for (int i = 0; i < tab.length ; i++)
                    setEntryAt(tab, i, null);       // entry都设置为指向null, 会被gc回收.
                ++modCount;
                count = 0;
            } finally {
                unlock();
            }
        }
    }

    // Accessing segments

    /**
     * Gets the jth element of given segment array (if nonnull) with
     * volatile element access semantics via Unsafe. (The null check
     * can trigger harmlessly only during deserialization.) Note:
     * because each element of segments array is set only once (using
     * fully ordered writes), some performance-sensitive methods rely
     * on this method only as a recheck upon null reads.
     */
    @SuppressWarnings("unchecked")
    static final <K,V> Segment<K,V> segmentAt(Segment<K,V>[] ss, int j) {
        long u = (j << SSHIFT) + SBASE;
        return ss == null ? null :
            (Segment<K,V>) UNSAFE.getObjectVolatile(ss, u);     // 这个segmentAt是读取的volatile,能保证可见性
    }

    /**
     * Returns the segment for the given index, creating it and
     * recording in segment table (via CAS) if not already present.
     *
     * @param k the index
     * @return the segment
     */
    @SuppressWarnings("unchecked")
    private Segment<K,V> ensureSegment(int k) {
        final Segment<K,V>[] ss = this.segments;
        long u = (k << SSHIFT) + SBASE; // raw offset
        Segment<K,V> seg;
        if ((seg = (Segment<K,V>)UNSAFE.getObjectVolatile(ss, u)) == null) {
            Segment<K,V> proto = ss[0]; // use segment 0 as prototype
            int cap = proto.table.length;
            float lf = proto.loadFactor;
            int threshold = (int)(cap * lf);
            HashEntry<K,V>[] tab = (HashEntry<K,V>[])new HashEntry[cap];
            if ((seg = (Segment<K,V>)UNSAFE.getObjectVolatile(ss, u))
                == null) { // recheck
                Segment<K,V> s = new Segment<K,V>(lf, threshold, tab);
                while ((seg = (Segment<K,V>)UNSAFE.getObjectVolatile(ss, u))
                       == null) {
                    if (UNSAFE.compareAndSwapObject(ss, u, null, seg = s))
                        break;
                }
            }
        }
        return seg;
    }

    // Hash-based segment and entry accesses

    /**
     * Get the segment for the given hash
     */
    @SuppressWarnings("unchecked")
    private Segment<K,V> segmentForHash(int h) {
        long u = (((h >>> segmentShift) & segmentMask) << SSHIFT) + SBASE;
        return (Segment<K,V>) UNSAFE.getObjectVolatile(segments, u);
    }

    /**
     * Gets the table entry for the given segment and hash                  // 根据入参segment和hash值,获取h对应的entry
     */
    @SuppressWarnings("unchecked")
    static final <K,V> HashEntry<K,V> entryForHash(Segment<K,V> seg, int h) {
        HashEntry<K,V>[] tab;
        return (seg == null || (tab = seg.table) == null) ? null :
            (HashEntry<K,V>) UNSAFE.getObjectVolatile
            (tab, ((long)(((tab.length - 1) & h)) << TSHIFT) + TBASE);  // 从table,根据便宜量,用UNSAFE获取HashEntry
    }

    /* ---------------- Public operations -------------- */

    /**
     * Creates a new, empty map with the specified initial                  // 根据指定的入参创建一个新的,空的map
     * capacity, load factor and concurrency level.
     *
     * @param initialCapacity the initial capacity. The implementation      // 初始容量, 用于容纳元素的
     * performs internal sizing to accommodate this many elements.
     * @param loadFactor  the load factor threshold, used to control resizing.  // 用来控制再hash的
     * Resizing may be performed when the average number of elements per
     * bin exceeds this threshold.
     * @param concurrencyLevel the estimated number of concurrently         // 用来适应这些线程数
     * updating threads. The implementation performs internal sizing
     * to try to accommodate this many threads.
     * @throws IllegalArgumentException if the initial capacity is
     * negative or the load factor or concurrencyLevel are
     * nonpositive.
     */
    @SuppressWarnings("unchecked")
    public ConcurrentHashMap(int initialCapacity,
                             float loadFactor, int concurrencyLevel) {
        if (!(loadFactor > 0) || initialCapacity < 0 || concurrencyLevel <= 0)  // 1.构造函数开始的地方,校验入参
            throw new IllegalArgumentException();
        if (concurrencyLevel > MAX_SEGMENTS)                        // 2.校验参数,并做相关转化
            concurrencyLevel = MAX_SEGMENTS;
        // Find power-of-two sizes best matching arguments          // 3.找到2的次方数,最匹配入参的
        int sshift = 0;                                             // 偏移的位数
        int ssize = 1;                                              // 容量
        while (ssize < concurrencyLevel) {                          // 只要小于指定的concurrencyLevel就一直倍增
            ++sshift;                                               // 倍增一次,sshift就加一.
            ssize <<= 1;                                            // 最后ssize就是大于等于concurrencyLevel的,最小的2的次方数,ssize就是下面要用的并发级别数了.
        }
        this.segmentShift = 32 - sshift;                            // segmentShift是用来定位segment的
        this.segmentMask = ssize - 1;                               // 掩码也是用来计算segment位置的, ssize就是segment size(segment的个数),掩码就是-1的数字了
        if (initialCapacity > MAXIMUM_CAPACITY)                     // 最大就为MAXIMUM_CAPACITY
            initialCapacity = MAXIMUM_CAPACITY;
        int c = initialCapacity / ssize;                            // 初始容量 除 segment个数 = 每个segment的初始容量
        if (c * ssize < initialCapacity)                            // 判断是否要加1
            ++c;
        int cap = MIN_SEGMENT_TABLE_CAPACITY;                       // 每个segment的最小容量
        while (cap < c)                                             // 一直倍增,直到大于c大于cap
            cap <<= 1;                                              // 得到的结果cap就是每个segment的初始容量
        // create segments and segments[0]                          // 这里只创建了segment数组和segments[0],剩余的会动态创建.
        Segment<K,V> s0 =                                           // 创建segment0
            new Segment<K,V>(loadFactor, (int)(cap * loadFactor),   // threadHold = cap X loadFactor
                             (HashEntry<K,V>[])new HashEntry[cap]); // 创建cap数量的HashEntry数组
        Segment<K,V>[] ss = (Segment<K,V>[])new Segment[ssize];     // 创建segment数组
        UNSAFE.putOrderedObject(ss, SBASE, s0); // ordered write of segments[0] // 对segments[0]的顺序写入,这里使用putOrderedObject是为了保证顺序一致性,而且更快
        this.segments = ss;                                         // 访问的时候也通过getObjectVolatile,就可以得到一致的结果
    }   // 从这个构造函数可以看出: 根据指定的并发级别,是先求出一个合法的并发级别数,这个数字就是segment的个数. 然后用initialCapacity初始容量 除 个数 得到每个segment的容量,容量还需要转化成2的次方数.

    /**
     * Creates a new, empty map with the specified initial capacity
     * and load factor and with the default concurrencyLevel (16).
     *
     * @param initialCapacity The implementation performs internal
     * sizing to accommodate this many elements.
     * @param loadFactor  the load factor threshold, used to control resizing.
     * Resizing may be performed when the average number of elements per
     * bin exceeds this threshold.
     * @throws IllegalArgumentException if the initial capacity of
     * elements is negative or the load factor is nonpositive
     *
     * @since 1.6
     */
    public ConcurrentHashMap(int initialCapacity, float loadFactor) {
        this(initialCapacity, loadFactor, DEFAULT_CONCURRENCY_LEVEL);
    }

    /**
     * Creates a new, empty map with the specified initial capacity,
     * and with default load factor (0.75) and concurrencyLevel (16).
     *
     * @param initialCapacity the initial capacity. The implementation
     * performs internal sizing to accommodate this many elements.
     * @throws IllegalArgumentException if the initial capacity of
     * elements is negative.
     */
    public ConcurrentHashMap(int initialCapacity) {
        this(initialCapacity, DEFAULT_LOAD_FACTOR, DEFAULT_CONCURRENCY_LEVEL);
    }

    /**
     * Creates a new, empty map with a default initial capacity (16),
     * load factor (0.75) and concurrencyLevel (16).
     */
    public ConcurrentHashMap() {
        this(DEFAULT_INITIAL_CAPACITY, DEFAULT_LOAD_FACTOR, DEFAULT_CONCURRENCY_LEVEL);
    }

    /**
     * Creates a new map with the same mappings as the given map.
     * The map is created with a capacity of 1.5 times the number
     * of mappings in the given map or 16 (whichever is greater),
     * and a default load factor (0.75) and concurrencyLevel (16).
     *
     * @param m the map
     */
    public ConcurrentHashMap(Map<? extends K, ? extends V> m) {
        this(Math.max((int) (m.size() / DEFAULT_LOAD_FACTOR) + 1,
                      DEFAULT_INITIAL_CAPACITY),
             DEFAULT_LOAD_FACTOR, DEFAULT_CONCURRENCY_LEVEL);
        putAll(m);
    }

    /**
     * Returns <tt>true</tt> if this map contains no key-value mappings.
     *
     * @return <tt>true</tt> if this map contains no key-value mappings
     */
    public boolean isEmpty() {
        /*
         * Sum per-segment modCounts to avoid mis-reporting when        // 对每个segment的modCount求和,避免错误的报告
         * elements are concurrently added and removed in one segment   // 当对同一个segment并发的添加和删除时
         * while checking another, in which case the table was never    // 当校验另一种情况, table从来没有真正的为空
         * actually empty at any point. (The sum ensures accuracy up    // sum求和保证精确度
         * through at least 1<<31 per-segment modifications before
         * recheck.)  Methods size() and containsValue() use similar    // size()方法和containsValue()方法和这个方法逻辑类似
         * constructions for stability checks.
         */
        long sum = 0L;      // 用于累计modCount的,因为累计count没有意义,只要一个count不为0,立马返回false了.
        final Segment<K,V>[] segments = this.segments;
        for (int j = 0; j < segments.length; ++j) {
            Segment<K,V> seg = segmentAt(segments, j);  // 找到第j个segment
            if (seg != null) {
                if (seg.count != 0)                     // 判断count是否为0
                    return false;                       // 只要有一个不为0,立马返回false
                sum += seg.modCount;
            }
        }
        if (sum != 0L) { // recheck unless no modifications // 如果sum不为0,重新检查一次
            for (int j = 0; j < segments.length; ++j) {
                Segment<K,V> seg = segmentAt(segments, j);
                if (seg != null) {
                    if (seg.count != 0)
                        return false;                   // 这一行上面逻辑都一样
                    sum -= seg.modCount;                // 这里是减去每个seg的modCount
                }
            }
            if (sum != 0L)                              // 计算得到的sum不为0,表示有变动了,是有新的元素插入了
                return false;                           // 返回false,表示非空
        }
        return true;
    }

    /**
     * Returns the number of key-value mappings in this map.  If the
     * map contains more than <tt>Integer.MAX_VALUE</tt> elements, returns
     * <tt>Integer.MAX_VALUE</tt>.
     *
     * @return the number of key-value mappings in this map
     */
    public int size() {
        // Try a few times to get accurate count. On failure due to
        // continuous async changes in table, resort to locking.
        final Segment<K,V>[] segments = this.segments;
        int size;
        boolean overflow; // true if size overflows 32 bits
        long sum;         // sum of modCounts
        long last = 0L;   // previous sum
        int retries = -1; // first iteration isn't retry
        try {
            for (;;) {
                if (retries++ == RETRIES_BEFORE_LOCK) {             // size()和contains方法先尝试两次
                    for (int j = 0; j < segments.length; ++j)       // 尝试失败,就会对所有的segment加锁
                        ensureSegment(j).lock(); // force creation
                }
                sum = 0L;
                size = 0;
                overflow = false;
                for (int j = 0; j < segments.length; ++j) {
                    Segment<K,V> seg = segmentAt(segments, j);
                    if (seg != null) {
                        sum += seg.modCount;
                        int c = seg.count;
                        if (c < 0 || (size += c) < 0)
                            overflow = true;
                    }
                }
                if (sum == last)                                // 校验是否有变化. 其实这也是CAS的理念. 判断是否有修改, 有修改了, 继续重试几次.
                    break;
                last = sum;
            }
        } finally {
            if (retries > RETRIES_BEFORE_LOCK) {
                for (int j = 0; j < segments.length; ++j)   // 在finally中释放锁!
                    segmentAt(segments, j).unlock();
            }
        }
        return overflow ? Integer.MAX_VALUE : size;
    }

    /**
     * Returns the value to which the specified key is mapped,
     * or {@code null} if this map contains no mapping for the key.
     *
     * <p>More formally, if this map contains a mapping from a key
     * {@code k} to a value {@code v} such that {@code key.equals(k)},
     * then this method returns {@code v}; otherwise it returns
     * {@code null}.  (There can be at most one such mapping.)
     *
     * @throws NullPointerException if the specified key is null
     */
    public V get(Object key) {
        Segment<K,V> s; // manually integrate access methods to reduce overhead (手工集成访问方法,用来减少开销.)
        HashEntry<K,V>[] tab;
        int h = hash(key);
        long u = (((h >>> segmentShift) & segmentMask) << SSHIFT) + SBASE;
        if ((s = (Segment<K,V>)UNSAFE.getObjectVolatile(segments, u)) != null &&    // get没有使用锁同步，而是使用轻量级同步volatile原语sun.misc.Unsafe.getObjectVolatile(Object, long)，
            (tab = s.table) != null) {                                              // 保证读到的是最新的对象。
            for (HashEntry<K,V> e = (HashEntry<K,V>) UNSAFE.getObjectVolatile
                     (tab, ((long)(((tab.length - 1) & h)) << TSHIFT) + TBASE);
                 e != null; e = e.next) {
                K k;
                if ((k = e.key) == key || (e.hash == h && key.equals(k)))
                    return e.value;
            }
        }
        return null;
    }

    /**
     * Tests if the specified object is a key in this table.
     *
     * @param  key   possible key
     * @return <tt>true</tt> if and only if the specified object
     *         is a key in this table, as determined by the
     *         <tt>equals</tt> method; <tt>false</tt> otherwise.
     * @throws NullPointerException if the specified key is null
     */
    @SuppressWarnings("unchecked")
    public boolean containsKey(Object key) {
        Segment<K,V> s; // same as get() except no need for volatile value read
        HashEntry<K,V>[] tab;
        int h = hash(key);
        long u = (((h >>> segmentShift) & segmentMask) << SSHIFT) + SBASE;
        if ((s = (Segment<K,V>)UNSAFE.getObjectVolatile(segments, u)) != null &&
            (tab = s.table) != null) {
            for (HashEntry<K,V> e = (HashEntry<K,V>) UNSAFE.getObjectVolatile
                     (tab, ((long)(((tab.length - 1) & h)) << TSHIFT) + TBASE);
                 e != null; e = e.next) {
                K k;
                if ((k = e.key) == key || (e.hash == h && key.equals(k)))
                    return true;
            }
        }
        return false;
    }

    /**
     * Returns <tt>true</tt> if this map maps one or more keys to the
     * specified value. Note: This method requires a full internal
     * traversal of the hash table, and so is much slower than
     * method <tt>containsKey</tt>.
     *
     * @param value value whose presence in this map is to be tested
     * @return <tt>true</tt> if this map maps one or more keys to the
     *         specified value
     * @throws NullPointerException if the specified value is null
     */
    public boolean containsValue(Object value) {
        // Same idea as size()
        if (value == null)
            throw new NullPointerException();
        final Segment<K,V>[] segments = this.segments;
        boolean found = false;
        long last = 0;
        int retries = -1;
        try {
            outer: for (;;) {
                if (retries++ == RETRIES_BEFORE_LOCK) {
                    for (int j = 0; j < segments.length; ++j)
                        ensureSegment(j).lock(); // force creation
                }
                long hashSum = 0L;
                int sum = 0;
                for (int j = 0; j < segments.length; ++j) {
                    HashEntry<K,V>[] tab;
                    Segment<K,V> seg = segmentAt(segments, j);
                    if (seg != null && (tab = seg.table) != null) {
                        for (int i = 0 ; i < tab.length; i++) {
                            HashEntry<K,V> e;
                            for (e = entryAt(tab, i); e != null; e = e.next) {
                                V v = e.value;
                                if (v != null && value.equals(v)) {
                                    found = true;
                                    break outer;
                                }
                            }
                        }
                        sum += seg.modCount;
                    }
                }
                if (retries > 0 && sum == last)
                    break;
                last = sum;
            }
        } finally {
            if (retries > RETRIES_BEFORE_LOCK) {
                for (int j = 0; j < segments.length; ++j)
                    segmentAt(segments, j).unlock();
            }
        }
        return found;
    }

    /**
     * Legacy method testing if some key maps into the specified value
     * in this table.  This method is identical in functionality to
     * {@link #containsValue}, and exists solely to ensure
     * full compatibility with class {@link java.util.Hashtable},
     * which supported this method prior to introduction of the
     * Java Collections framework.

     * @param  value a value to search for
     * @return <tt>true</tt> if and only if some key maps to the
     *         <tt>value</tt> argument in this table as
     *         determined by the <tt>equals</tt> method;
     *         <tt>false</tt> otherwise
     * @throws NullPointerException if the specified value is null
     */
    public boolean contains(Object value) {
        return containsValue(value);
    }

    /**
     * Maps the specified key to the specified value in this table.
     * Neither the key nor the value can be null.
     *
     * <p> The value can be retrieved by calling the <tt>get</tt> method
     * with a key that is equal to the original key.
     *
     * @param key key with which the specified value is to be associated
     * @param value value to be associated with the specified key
     * @return the previous value associated with <tt>key</tt>, or
     *         <tt>null</tt> if there was no mapping for <tt>key</tt>
     * @throws NullPointerException if the specified key or value is null
     */
    @SuppressWarnings("unchecked")
    public V put(K key, V value) {
        Segment<K,V> s;
        if (value == null)
            throw new NullPointerException();
        int hash = hash(key);
        int j = (hash >>> segmentShift) & segmentMask;
        if ((s = (Segment<K,V>)UNSAFE.getObject          // nonvolatile; recheck
             (segments, (j << SSHIFT) + SBASE)) == null) //  in ensureSegment
            s = ensureSegment(j);
        return s.put(key, hash, value, false);
    }

    /**
     * {@inheritDoc}
     *
     * @return the previous value associated with the specified key,
     *         or <tt>null</tt> if there was no mapping for the key
     * @throws NullPointerException if the specified key or value is null
     */
    @SuppressWarnings("unchecked")
    public V putIfAbsent(K key, V value) {              // putIfAbsent操作是线程安全的,就是这个方法只会执行一次
        Segment<K,V> s;
        if (value == null)
            throw new NullPointerException();
        int hash = hash(key);                           // 找到key的hash
        int j = (hash >>> segmentShift) & segmentMask;
        if ((s = (Segment<K,V>)UNSAFE.getObject
             (segments, (j << SSHIFT) + SBASE)) == null)
            s = ensureSegment(j);                       // 根据index,找到对应的segment
        return s.put(key, hash, value, true);   // 这里保证了只放一次,因为put的时候加锁了,但是只对当前的segment加锁了,锁的粒度相对小一些.
    }

    /**
     * Copies all of the mappings from the specified map to this one.
     * These mappings replace any mappings that this map had for any of the
     * keys currently in the specified map.
     *
     * @param m mappings to be stored in this map
     */
    public void putAll(Map<? extends K, ? extends V> m) {
        for (Map.Entry<? extends K, ? extends V> e : m.entrySet())
            put(e.getKey(), e.getValue());
    }

    /**
     * Removes the key (and its corresponding value) from this map.
     * This method does nothing if the key is not in the map.
     *
     * @param  key the key that needs to be removed
     * @return the previous value associated with <tt>key</tt>, or
     *         <tt>null</tt> if there was no mapping for <tt>key</tt>
     * @throws NullPointerException if the specified key is null
     */
    public V remove(Object key) {
        int hash = hash(key);
        Segment<K,V> s = segmentForHash(hash);
        return s == null ? null : s.remove(key, hash, null);
    }

    /**
     * {@inheritDoc}
     *
     * @throws NullPointerException if the specified key is null
     */
    public boolean remove(Object key, Object value) {
        int hash = hash(key);
        Segment<K,V> s;
        return value != null && (s = segmentForHash(hash)) != null &&
            s.remove(key, hash, value) != null;
    }

    /**
     * {@inheritDoc}
     *
     * @throws NullPointerException if any of the arguments are null
     */
    public boolean replace(K key, V oldValue, V newValue) {
        int hash = hash(key);
        if (oldValue == null || newValue == null)
            throw new NullPointerException();
        Segment<K,V> s = segmentForHash(hash);
        return s != null && s.replace(key, hash, oldValue, newValue);
    }

    /**
     * {@inheritDoc}
     *
     * @return the previous value associated with the specified key,
     *         or <tt>null</tt> if there was no mapping for the key
     * @throws NullPointerException if the specified key or value is null
     */
    public V replace(K key, V value) {
        int hash = hash(key);
        if (value == null)
            throw new NullPointerException();
        Segment<K,V> s = segmentForHash(hash);
        return s == null ? null : s.replace(key, hash, value);
    }

    /**
     * Removes all of the mappings from this map.
     */
    public void clear() {
        final Segment<K,V>[] segments = this.segments;
        for (int j = 0; j < segments.length; ++j) {
            Segment<K,V> s = segmentAt(segments, j);
            if (s != null)
                s.clear();
        }
    }

    /**
     * Returns a {@link Set} view of the keys contained in this map.
     * The set is backed by the map, so changes to the map are
     * reflected in the set, and vice-versa.  The set supports element
     * removal, which removes the corresponding mapping from this map,
     * via the <tt>Iterator.remove</tt>, <tt>Set.remove</tt>,
     * <tt>removeAll</tt>, <tt>retainAll</tt>, and <tt>clear</tt>
     * operations.  It does not support the <tt>add</tt> or
     * <tt>addAll</tt> operations.
     *
     * <p>The view's <tt>iterator</tt> is a "weakly consistent" iterator
     * that will never throw {@link ConcurrentModificationException},
     * and guarantees to traverse elements as they existed upon
     * construction of the iterator, and may (but is not guaranteed to)
     * reflect any modifications subsequent to construction.
     */
    public Set<K> keySet() {
        Set<K> ks = keySet;
        return (ks != null) ? ks : (keySet = new KeySet());
    }

    /**
     * Returns a {@link Collection} view of the values contained in this map.
     * The collection is backed by the map, so changes to the map are
     * reflected in the collection, and vice-versa.  The collection
     * supports element removal, which removes the corresponding
     * mapping from this map, via the <tt>Iterator.remove</tt>,
     * <tt>Collection.remove</tt>, <tt>removeAll</tt>,
     * <tt>retainAll</tt>, and <tt>clear</tt> operations.  It does not
     * support the <tt>add</tt> or <tt>addAll</tt> operations.
     *
     * <p>The view's <tt>iterator</tt> is a "weakly consistent" iterator
     * that will never throw {@link ConcurrentModificationException},
     * and guarantees to traverse elements as they existed upon
     * construction of the iterator, and may (but is not guaranteed to)
     * reflect any modifications subsequent to construction.
     */
    public Collection<V> values() {
        Collection<V> vs = values;
        return (vs != null) ? vs : (values = new Values());
    }

    /**
     * Returns a {@link Set} view of the mappings contained in this map.
     * The set is backed by the map, so changes to the map are
     * reflected in the set, and vice-versa.  The set supports element
     * removal, which removes the corresponding mapping from the map,
     * via the <tt>Iterator.remove</tt>, <tt>Set.remove</tt>,
     * <tt>removeAll</tt>, <tt>retainAll</tt>, and <tt>clear</tt>
     * operations.  It does not support the <tt>add</tt> or
     * <tt>addAll</tt> operations.
     *
     * <p>The view's <tt>iterator</tt> is a "weakly consistent" iterator
     * that will never throw {@link ConcurrentModificationException},
     * and guarantees to traverse elements as they existed upon
     * construction of the iterator, and may (but is not guaranteed to)
     * reflect any modifications subsequent to construction.
     */
    public Set<Map.Entry<K,V>> entrySet() {
        Set<Map.Entry<K,V>> es = entrySet;
        return (es != null) ? es : (entrySet = new EntrySet());
    }

    /**
     * Returns an enumeration of the keys in this table.
     *
     * @return an enumeration of the keys in this table
     * @see #keySet()
     */
    public Enumeration<K> keys() {
        return new KeyIterator();
    }

    /**
     * Returns an enumeration of the values in this table.
     *
     * @return an enumeration of the values in this table
     * @see #values()
     */
    public Enumeration<V> elements() {
        return new ValueIterator();
    }

    /* ---------------- Iterator Support -------------- */

    abstract class HashIterator {
        int nextSegmentIndex;
        int nextTableIndex;
        HashEntry<K,V>[] currentTable;
        HashEntry<K, V> nextEntry;
        HashEntry<K, V> lastReturned;

        HashIterator() {
            nextSegmentIndex = segments.length - 1;
            nextTableIndex = -1;
            advance();
        }

        /**
         * Set nextEntry to first node of next non-empty table
         * (in backwards order, to simplify checks).
         */
        final void advance() {
            for (;;) {
                if (nextTableIndex >= 0) {
                    if ((nextEntry = entryAt(currentTable,
                                             nextTableIndex--)) != null)
                        break;
                }
                else if (nextSegmentIndex >= 0) {
                    Segment<K,V> seg = segmentAt(segments, nextSegmentIndex--);
                    if (seg != null && (currentTable = seg.table) != null)
                        nextTableIndex = currentTable.length - 1;
                }
                else
                    break;
            }
        }

        final HashEntry<K,V> nextEntry() {
            HashEntry<K,V> e = nextEntry;
            if (e == null)
                throw new NoSuchElementException();
            lastReturned = e; // cannot assign until after null check
            if ((nextEntry = e.next) == null)
                advance();
            return e;
        }

        public final boolean hasNext() { return nextEntry != null; }
        public final boolean hasMoreElements() { return nextEntry != null; }

        public final void remove() {
            if (lastReturned == null)
                throw new IllegalStateException();
            ConcurrentHashMap.this.remove(lastReturned.key);
            lastReturned = null;
        }
    }

    final class KeyIterator
        extends HashIterator
        implements Iterator<K>, Enumeration<K>
    {
        public final K next()        { return super.nextEntry().key; }
        public final K nextElement() { return super.nextEntry().key; }
    }

    final class ValueIterator
        extends HashIterator
        implements Iterator<V>, Enumeration<V>
    {
        public final V next()        { return super.nextEntry().value; }
        public final V nextElement() { return super.nextEntry().value; }
    }

    /**
     * Custom Entry class used by EntryIterator.next(), that relays
     * setValue changes to the underlying map.
     */
    final class WriteThroughEntry
        extends AbstractMap.SimpleEntry<K,V>
    {
        WriteThroughEntry(K k, V v) {
            super(k,v);
        }

        /**
         * Set our entry's value and write through to the map. The
         * value to return is somewhat arbitrary here. Since a
         * WriteThroughEntry does not necessarily track asynchronous
         * changes, the most recent "previous" value could be
         * different from what we return (or could even have been
         * removed in which case the put will re-establish). We do not
         * and cannot guarantee more.
         */
        public V setValue(V value) {
            if (value == null) throw new NullPointerException();
            V v = super.setValue(value);
            ConcurrentHashMap.this.put(getKey(), value);
            return v;
        }
    }

    final class EntryIterator
        extends HashIterator
        implements Iterator<Entry<K,V>>
    {
        public Map.Entry<K,V> next() {
            HashEntry<K,V> e = super.nextEntry();
            return new WriteThroughEntry(e.key, e.value);
        }
    }

    final class KeySet extends AbstractSet<K> {
        public Iterator<K> iterator() {
            return new KeyIterator();
        }
        public int size() {
            return ConcurrentHashMap.this.size();
        }
        public boolean isEmpty() {
            return ConcurrentHashMap.this.isEmpty();
        }
        public boolean contains(Object o) {
            return ConcurrentHashMap.this.containsKey(o);
        }
        public boolean remove(Object o) {
            return ConcurrentHashMap.this.remove(o) != null;
        }
        public void clear() {
            ConcurrentHashMap.this.clear();
        }
    }

    final class Values extends AbstractCollection<V> {
        public Iterator<V> iterator() {
            return new ValueIterator();
        }
        public int size() {
            return ConcurrentHashMap.this.size();
        }
        public boolean isEmpty() {
            return ConcurrentHashMap.this.isEmpty();
        }
        public boolean contains(Object o) {
            return ConcurrentHashMap.this.containsValue(o);
        }
        public void clear() {
            ConcurrentHashMap.this.clear();
        }
    }

    final class EntrySet extends AbstractSet<Map.Entry<K,V>> {
        public Iterator<Map.Entry<K,V>> iterator() {
            return new EntryIterator();
        }
        public boolean contains(Object o) {
            if (!(o instanceof Map.Entry))
                return false;
            Map.Entry<?,?> e = (Map.Entry<?,?>)o;
            V v = ConcurrentHashMap.this.get(e.getKey());
            return v != null && v.equals(e.getValue());
        }
        public boolean remove(Object o) {
            if (!(o instanceof Map.Entry))
                return false;
            Map.Entry<?,?> e = (Map.Entry<?,?>)o;
            return ConcurrentHashMap.this.remove(e.getKey(), e.getValue());
        }
        public int size() {
            return ConcurrentHashMap.this.size();
        }
        public boolean isEmpty() {
            return ConcurrentHashMap.this.isEmpty();
        }
        public void clear() {
            ConcurrentHashMap.this.clear();
        }
    }

    /* ---------------- Serialization Support -------------- */

    /**
     * Save the state of the <tt>ConcurrentHashMap</tt> instance to a
     * stream (i.e., serialize it).
     * @param s the stream
     * @serialData
     * the key (Object) and value (Object)
     * for each key-value mapping, followed by a null pair.
     * The key-value mappings are emitted in no particular order.
     */
    private void writeObject(java.io.ObjectOutputStream s) throws IOException {
        // force all segments for serialization compatibility
        for (int k = 0; k < segments.length; ++k)
            ensureSegment(k);
        s.defaultWriteObject();

        final Segment<K,V>[] segments = this.segments;
        for (int k = 0; k < segments.length; ++k) {
            Segment<K,V> seg = segmentAt(segments, k);
            seg.lock();
            try {
                HashEntry<K,V>[] tab = seg.table;
                for (int i = 0; i < tab.length; ++i) {
                    HashEntry<K,V> e;
                    for (e = entryAt(tab, i); e != null; e = e.next) {
                        s.writeObject(e.key);
                        s.writeObject(e.value);
                    }
                }
            } finally {
                seg.unlock();
            }
        }
        s.writeObject(null);
        s.writeObject(null);
    }

    /**
     * Reconstitute the <tt>ConcurrentHashMap</tt> instance from a
     * stream (i.e., deserialize it).
     * @param s the stream
     */
    @SuppressWarnings("unchecked")
    private void readObject(java.io.ObjectInputStream s)
        throws IOException, ClassNotFoundException {
        // Don't call defaultReadObject()
        ObjectInputStream.GetField oisFields = s.readFields();
        final Segment<K,V>[] oisSegments = (Segment<K,V>[])oisFields.get("segments", null);

        final int ssize = oisSegments.length;
        if (ssize < 1 || ssize > MAX_SEGMENTS
            || (ssize & (ssize-1)) != 0 )  // ssize not power of two
            throw new java.io.InvalidObjectException("Bad number of segments:"
                                                     + ssize);
        int sshift = 0, ssizeTmp = ssize;
        while (ssizeTmp > 1) {
            ++sshift;
            ssizeTmp >>>= 1;
        }
        UNSAFE.putIntVolatile(this, SEGSHIFT_OFFSET, 32 - sshift);
        UNSAFE.putIntVolatile(this, SEGMASK_OFFSET, ssize - 1);
        UNSAFE.putObjectVolatile(this, SEGMENTS_OFFSET, oisSegments);

        // set hashMask
        UNSAFE.putIntVolatile(this, HASHSEED_OFFSET, randomHashSeed(this));

        // Re-initialize segments to be minimally sized, and let grow.
        int cap = MIN_SEGMENT_TABLE_CAPACITY;
        final Segment<K,V>[] segments = this.segments;
        for (int k = 0; k < segments.length; ++k) {
            Segment<K,V> seg = segments[k];
            if (seg != null) {
                seg.threshold = (int)(cap * seg.loadFactor);
                seg.table = (HashEntry<K,V>[]) new HashEntry[cap];
            }
        }

        // Read the keys and values, and put the mappings in the table
        for (;;) {
            K key = (K) s.readObject();
            V value = (V) s.readObject();
            if (key == null)
                break;
            put(key, value);
        }
    }

    // Unsafe mechanics
    private static final sun.misc.Unsafe UNSAFE;
    private static final long SBASE;
    private static final int SSHIFT;
    private static final long TBASE;
    private static final int TSHIFT;
    private static final long HASHSEED_OFFSET;
    private static final long SEGSHIFT_OFFSET;
    private static final long SEGMASK_OFFSET;
    private static final long SEGMENTS_OFFSET;

    static {
        int ss, ts;
        try {
            UNSAFE = sun.misc.Unsafe.getUnsafe();
            Class tc = HashEntry[].class;
            Class sc = Segment[].class;
            TBASE = UNSAFE.arrayBaseOffset(tc);
            SBASE = UNSAFE.arrayBaseOffset(sc);
            ts = UNSAFE.arrayIndexScale(tc);
            ss = UNSAFE.arrayIndexScale(sc);
            HASHSEED_OFFSET = UNSAFE.objectFieldOffset(
                ConcurrentHashMap.class.getDeclaredField("hashSeed"));
            SEGSHIFT_OFFSET = UNSAFE.objectFieldOffset(
                ConcurrentHashMap.class.getDeclaredField("segmentShift"));
            SEGMASK_OFFSET = UNSAFE.objectFieldOffset(
                ConcurrentHashMap.class.getDeclaredField("segmentMask"));
            SEGMENTS_OFFSET = UNSAFE.objectFieldOffset(
                ConcurrentHashMap.class.getDeclaredField("segments"));
        } catch (Exception e) {
            throw new Error(e);
        }
        if ((ss & (ss-1)) != 0 || (ts & (ts-1)) != 0)
            throw new Error("data type scale not a power of two");
        SSHIFT = 31 - Integer.numberOfLeadingZeros(ss);
        TSHIFT = 31 - Integer.numberOfLeadingZeros(ts);
    }

}
