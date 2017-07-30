package io.swagger.scala.converter

import java.lang.annotation.Annotation
import java.lang.reflect.Type
import java.util.Iterator

import com.fasterxml.jackson.databind.`type`.ReferenceType
import com.fasterxml.jackson.module.scala.DefaultScalaModule
import io.swagger.converter._
import io.swagger.oas.models.media.{Schema, StringSchema}
import io.swagger.util.{Json, PrimitiveType}

import scala.collection.JavaConverters._

object SwaggerScalaModelConverter {
  Json.mapper().registerModule(new DefaultScalaModule())
}

class SwaggerScalaModelConverter extends ModelConverter {
  SwaggerScalaModelConverter

  override def resolve(`type`: Type, context: ModelConverterContext,
    annotations: Array[Annotation], chain: Iterator[ModelConverter]): Schema[_] = {
    val javaType = Json.mapper().constructType(`type`)
    val cls = javaType.getRawClass

    if(cls != null) {
      // handle scala enums
      getEnumerationInstance(cls) match {
        case Some(enumInstance) =>
          if (enumInstance.values != null) {
            val sp = new StringSchema()
            for (v <- enumInstance.values)
              sp.addEnumItem(v.toString)
            sp.addRequiredItem(`type`.getTypeName)
            return sp
          }
        case None =>
          if (cls.isAssignableFrom(classOf[BigDecimal])) {
            val dp = PrimitiveType.DECIMAL.createProperty()
            dp.addRequiredItem(`type`.getTypeName)
            return dp
          } else if (cls.isAssignableFrom(classOf[BigInt])) {
            val ip = PrimitiveType.INT.createProperty()
            ip.addRequiredItem(`type`.getTypeName)
            return ip
          }
      }
    }

    // Unbox scala options
    `type` match {
      case rt: ReferenceType if isOption(cls) =>
        val nextType = rt.getContentType
        val nextResolved = {
          Option(resolve(nextType, context, annotations, chain)) match {
            case Some(p) => Some(p)
            case None if chain.hasNext() =>
              Option(chain.next().resolve(nextType, context, annotations, chain))
            case _ => None
          }
        }
        nextResolved match {
          case Some(nextResolved) =>
            nextResolved.setRequired(List.empty[String].asJava)
            nextResolved
          case None => null
        }
      case t if chain.hasNext =>
        val nextResolved = chain.next().resolve(t, context, annotations, chain)
        nextResolved.addRequiredItem(t.getTypeName)
        nextResolved
      case _ =>
        null
    }
  }

  override def resolve(`type`: Type, context: ModelConverterContext, chain: Iterator[ModelConverter]): Schema[_] = {
    val javaType = Json.mapper().constructType(`type`)
    getEnumerationInstance(javaType.getRawClass) match {
      case Some(enumInstance) => null // ignore scala enums
      case None =>
        if (chain.hasNext()) {
          val next = chain.next()
          next.resolve(`type`, context, chain)
        }
        else
          null
    }
  }

  private def getEnumerationInstance(cls: Class[_]): Option[Enumeration] =
  {
    if (cls.getFields.map(_.getName).contains("MODULE$")) {
      val javaUniverse = scala.reflect.runtime.universe
      val m = javaUniverse.runtimeMirror(Thread.currentThread().getContextClassLoader)
      val moduleMirror = m.reflectModule(m.staticModule(cls.getName))
      moduleMirror.instance match
      {
        case enumInstance: Enumeration => Some(enumInstance)
        case _ => None
      }
    }
    else {
      None
    }
  }

  private def isOption(cls: Class[_]): Boolean = cls == classOf[scala.Option[_]]

}
