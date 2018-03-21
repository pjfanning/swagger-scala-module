package io.swagger.scala.converter

import java.lang.annotation.Annotation
import java.lang.reflect.Type
import java.util.Iterator
import java.util.function.BiFunction

import com.fasterxml.jackson.databind.JavaType
import com.fasterxml.jackson.databind.`type`.ReferenceType
import com.fasterxml.jackson.databind.introspect.Annotated
import com.fasterxml.jackson.module.scala.DefaultScalaModule
import io.swagger.v3.core.converter._
import io.swagger.v3.core.util.{Json, PrimitiveType}
import io.swagger.v3.oas.models.media.{Schema, StringSchema}

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
            //sp.setRequired(true)
            return sp
          }
        case None =>
          if (cls.isAssignableFrom(classOf[BigDecimal])) {
            val dp = PrimitiveType.DECIMAL.createProperty()
            //dp.setRequired(true)
            return dp
          } else if (cls.isAssignableFrom(classOf[BigInt])) {
            val ip = PrimitiveType.INT.createProperty()
            //ip.setRequired(true)
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
            case None if chain.hasNext =>
              Option(chain.next().resolve(nextType, context, annotations, chain))
            case _ => None
          }
        }
        nextResolved match {
          case Some(property) =>
            //property.setRequired(false)
            property
          case None => null
        }
      case t if chain.hasNext =>
        val nextResolved = Option(chain.next().resolve(t, context, annotations, chain))
        nextResolved match {
          case Some(property) =>
            //property.setRequired(true)
            property
          case None => null
        }
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

  def resolveAnnotatedType(`type`: Type, member: Annotated, elementName: String,
                           parent: Schema[_],
                           jsonUnwrappedHandler: BiFunction[JavaType, Array[Annotation], Schema[_]],
                           context: ModelConverterContext,
                           chain: Iterator[ModelConverter]): Schema[_] = {
    Option(resolve(`type`, context, chain)) match {
      case Some(s) => s
      case _ => {
        if (chain.hasNext()) {
          chain.next().resolveAnnotatedType(`type`, member, elementName, parent, jsonUnwrappedHandler, context, chain)
        } else {
          null
        }
      }
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
