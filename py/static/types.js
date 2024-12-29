
// @ts-check

/**
 * @typedef {[
 *   dt: number,
 *   gx: number,
 *   gy: number,
 *   gz: number,
 *   r:  number,
 *   g:  number,
 *   b:  number
 * ]} SensorData */

/**
 * Parses a a SensorData buffer in big-endian format
 * @param {ArrayBuffer} buffer - Buffer containing one uint32 followed by float32 values
 * @returns {SensorData} Parsed data
 * @throws {Error} If buffer length is incorrect
 */
export function parseSensorData(buffer) {
  const expectedBytes = 4 + (6 * 4);
  if (buffer.byteLength !== expectedBytes) {
    throw new Error(`Buffer must be exactly ${expectedBytes} bytes (got ${buffer.byteLength})`);
  }

  const dataView = new DataView(buffer);
  const result = new Array(7);

  result[0] = dataView.getUint32(0, false);

  for (let i = 0; i < 6; i++) {
    result[i + 1] = dataView.getFloat32(4 + (i * 4), false);
  }

  return /** @type {SensorData} */ (result);
}
