/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.avro.specific;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Map;

import org.apache.avro.Schema;
import org.apache.avro.Protocol;
import org.apache.avro.Protocol.Message;
import org.apache.avro.AvroRuntimeException;
import org.apache.avro.io.DatumReader;
import org.apache.avro.io.DatumWriter;
import org.apache.avro.io.Decoder;
import org.apache.avro.io.Encoder;
import org.apache.avro.ipc.AvroRemoteException;
import org.apache.avro.ipc.Responder;

/** {@link org.apache.avro.ipc.Responder Responder} for generated interfaces.*/
public class SpecificResponder extends Responder {
  private Object impl;

  public SpecificResponder(Class iface, Object impl) {
    this(SpecificData.get().getProtocol(iface), impl);
  }
    
  public SpecificResponder(Protocol protocol, Object impl) {
    super(protocol);
    this.impl = impl;
  }

  protected DatumWriter<Object> getDatumWriter(Schema schema) {
    return new SpecificDatumWriter(schema);
  }

  protected DatumReader<Object> getDatumReader(Schema schema) {
    return new SpecificDatumReader(schema);
  }

  @Override
  public Object readRequest(Schema schema, Decoder in) throws IOException {
    Object[] args = new Object[schema.getFields().size()];
    int i = 0;
    for (Map.Entry<String, Schema> param : schema.getFieldSchemas())
      args[i++] = getDatumReader(param.getValue()).read(null, in);
    return args;
  }

  @Override
  public void writeResponse(Schema schema, Object response, Encoder out)
    throws IOException {
    getDatumWriter(schema).write(response, out);
  }

  @Override
  public void writeError(Schema schema, AvroRemoteException error,
                         Encoder out) throws IOException {
    getDatumWriter(schema).write(error, out);
  }

  @Override
  public Object respond(Message message, Object request)
    throws AvroRemoteException {
    Class[] paramTypes = new Class[message.getRequest().getFields().size()];
    int i = 0;
    try {
      for (Map.Entry<String,Schema> param: message.getRequest().getFieldSchemas())
        paramTypes[i++] = SpecificData.get().getClass(param.getValue());
      Method method = impl.getClass().getMethod(message.getName(), paramTypes);
      return method.invoke(impl, (Object[])request);
    } catch (InvocationTargetException e) {
      Throwable target = e.getTargetException();
      if (target instanceof AvroRemoteException)
        throw (AvroRemoteException)target;
      else throw new AvroRuntimeException(e);
    } catch (NoSuchMethodException e) {
      throw new AvroRuntimeException(e);
    } catch (IllegalAccessException e) {
      throw new AvroRuntimeException(e);
    }
  }

}

