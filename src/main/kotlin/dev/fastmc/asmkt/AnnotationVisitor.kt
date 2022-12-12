package dev.fastmc.asmkt

import it.unimi.dsi.fastutil.objects.ObjectArrayList
import org.objectweb.asm.AnnotationVisitor
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.AnnotationNode
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap as O2OHashMap

typealias AnnotationValueVisit<T> = (value: T) -> Unit
typealias AnnotationArrayVisit<T> = (values: List<T>) -> Unit
typealias AnnotationEnumVisit = (value: String) -> Unit
typealias AnnotationAnnotationVisit = AnnotationVisitorBuilder.() -> Unit

class AnnotationVisitorBuilder {
    private val valueVisits = O2OHashMap<Class<*>, O2OHashMap<String, AnnotationValueVisit<Any>>>()
    private val arrayVisits = O2OHashMap<Class<*>, O2OHashMap<String, AnnotationArrayVisit<Any>>>()
    private val enumVisits = O2OHashMap<String, O2OHashMap<String, AnnotationEnumVisit>>()
    private val annotationVisits = O2OHashMap<String, O2OHashMap<String, AnnotationAnnotationVisit>>()

    fun <T> visitValue(type: Class<T>, name: String, block: AnnotationValueVisit<T>) {
        @Suppress("UNCHECKED_CAST")
        require(valueVisits.getOrPut(type, ::O2OHashMap).put(name, block as AnnotationValueVisit<Any>) == null) { "Already visited $name:$type" }
    }

    inline fun <reified T> visitValue(name: String, noinline block: AnnotationValueVisit<T>) {
        visitValue(T::class.java, name, block)
    }

    fun <T> visitArray(type: Class<T>, name: String, block: AnnotationArrayVisit<T>) {
        @Suppress("UNCHECKED_CAST")
        require(arrayVisits.getOrPut(type, ::O2OHashMap).put(name, block as AnnotationArrayVisit<Any>) == null) { "Already visited array $name:$type" }
    }

    inline fun <reified T> visitArray(name: String, noinline block: AnnotationArrayVisit<T>) {
        visitArray(T::class.java, name, block)
    }

    fun visitEnum(name: String, desc: String, block: AnnotationEnumVisit) {
        require(enumVisits.getOrPut(name, ::O2OHashMap).put(desc, block) == null) { "Already visited enum $name:$desc" }
    }

    fun visitAnnotation(name: String, desc: String, block: AnnotationAnnotationVisit) {
        require(annotationVisits.getOrPut(name, ::O2OHashMap).put(desc, block) == null) { "Already visited annotation $name:$desc" }
    }

    fun build(): AnnotationVisitor {
        return object : AnnotationVisitor(Opcodes.ASM9) {
            override fun visit(name: String, value: Any?) {
                if (value != null) {
                    valueVisits[value::class.java]?.get(name)?.invoke(value)
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
                        values?.let { arrayVisits[type!!]?.get(name)?.invoke(it) }
                    }
                }
            }

            override fun visitEnum(name: String, descriptor: String, value: String) {
                enumVisits[name]?.get(descriptor)?.invoke(value)
            }

            override fun visitAnnotation(name: String, descriptor: String): AnnotationVisitor? {
                return annotationVisits[name]?.get(descriptor)?.let { AnnotationVisitorBuilder().apply(it).build() }
            }
        }
    }
}

inline fun AnnotationNode.visit(block: AnnotationVisitorBuilder.() -> Unit) {
    accept(AnnotationVisitorBuilder().apply(block).build())
}