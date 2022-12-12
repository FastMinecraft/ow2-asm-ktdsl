package dev.fastmc.asmkt

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap
import it.unimi.dsi.fastutil.objects.ObjectArrayList
import org.objectweb.asm.AnnotationVisitor
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.AnnotationNode

typealias AnnotationValueVisit<T> = (name: String, value: T) -> Unit
typealias AnnotationArrayVisit<T> = (name: String, values: List<T>) -> Unit
typealias AnnotationEnumVisit = (name: String, descriptor: String, value: String) -> Unit

class AnnotationVisitorBuilder {
    private val visits = Object2ObjectOpenHashMap<Class<*>, AnnotationValueVisit<Any>>()
    private val arrayVisits = Object2ObjectOpenHashMap<Class<*>, AnnotationArrayVisit<Any>>()
    private val enumVisits = ObjectArrayList<AnnotationEnumVisit>()

    fun <T> visit(type: Class<T>, block: AnnotationValueVisit<T>) {
        @Suppress("UNCHECKED_CAST")
        require(visits.put(type, block as AnnotationValueVisit<Any>) == null) { "Already visited $type" }
    }

    inline fun <reified T> visit(noinline block: AnnotationValueVisit<T>) {
        visit(T::class.java, block)
    }

    fun <T> visitArray(type: Class<T>, block: AnnotationArrayVisit<T>) {
        @Suppress("UNCHECKED_CAST")
        require(arrayVisits.put(type, block as AnnotationArrayVisit<Any>) == null) { "Already visited array $type" }
    }

    inline fun <reified T> visitArray(noinline block: AnnotationArrayVisit<T>) {
        visitArray(T::class.java, block)
    }

    fun visitEnum(block: AnnotationEnumVisit) {
        enumVisits.add(block)
    }

    fun build(): AnnotationVisitor {
        return object : AnnotationVisitor(Opcodes.ASM9) {
            override fun visit(name: String, value: Any?) {
                if (value != null) {
                    visits[value::class.java]?.invoke(name, value)
                }
            }

            override fun visitArray(name: String): AnnotationVisitor {
                return object : AnnotationVisitor(Opcodes.ASM9) {
                    private var values: ObjectArrayList<Any>? = null
                    private var type: Class<*>? = null

                    override fun visit(name: String?, value: Any) {
                        if (values == null) {
                            values = ObjectArrayList<Any>()
                            type = value::class.java
                        } else {
                            require(type == value::class.java) { "Array type mismatch" }
                        }
                        values!!.add(value)
                    }

                    override fun visitEnd() {
                        values?.let { arrayVisits[type!!]?.invoke(name, it) }
                    }
                }
            }

            override fun visitEnum(name: String, descriptor: String, value: String) {
                enumVisits.forEach { it.invoke(name, descriptor, value) }
            }
        }
    }
}

inline fun AnnotationNode.visit(block: AnnotationVisitorBuilder.() -> Unit) {
    accept(AnnotationVisitorBuilder().apply(block).build())
}