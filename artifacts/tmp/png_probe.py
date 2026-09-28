"""纯 Python PNG 读取 + 关键区域采样，用于把截图原型量成可核对的数字。"""
import zlib, struct, sys


def load_png(path):
    data = open(path, 'rb').read()
    assert data[:8] == b'\x89PNG\r\n\x1a\n'
    pos = 8
    idat = b''
    w = h = bd = ct = None
    while pos < len(data):
        ln = struct.unpack('>I', data[pos:pos + 4])[0]
        typ = data[pos + 4:pos + 8]
        chunk = data[pos + 8:pos + 8 + ln]
        if typ == b'IHDR':
            w, h, bd, ct, comp, filt, inter = struct.unpack('>IIBBBBB', chunk)
        elif typ == b'IDAT':
            idat += chunk
        elif typ == b'IEND':
            break
        pos += 12 + ln
    raw = zlib.decompress(idat)
    ch = {0: 1, 2: 3, 3: 1, 4: 2, 6: 4}[ct]
    assert bd == 8, f'bitdepth {bd}'
    stride = w * ch
    out = bytearray(h * stride)
    prev = bytearray(stride)
    p = 0
    for y in range(h):
        f = raw[p]; p += 1
        line = bytearray(raw[p:p + stride]); p += stride
        if f == 1:
            for i in range(ch, stride):
                line[i] = (line[i] + line[i - ch]) & 255
        elif f == 2:
            for i in range(stride):
                line[i] = (line[i] + prev[i]) & 255
        elif f == 3:
            for i in range(stride):
                a = line[i - ch] if i >= ch else 0
                line[i] = (line[i] + ((a + prev[i]) >> 1)) & 255
        elif f == 4:
            for i in range(stride):
                a = line[i - ch] if i >= ch else 0
                b = prev[i]
                c = prev[i - ch] if i >= ch else 0
                pa = abs(b - c); pb = abs(a - c); pc = abs(a + b - 2 * c)
                pr = a if (pa <= pb and pa <= pc) else (b if pb <= pc else c)
                line[i] = (line[i] + pr) & 255
        out[y * stride:(y + 1) * stride] = line
        prev = line
    return w, h, ch, out


class Img:
    def __init__(self, path):
        self.w, self.h, self.ch, self.buf = load_png(path)

    def px(self, x, y):
        i = (y * self.w + x) * self.ch
        return tuple(self.buf[i:i + 3])

    def row_span(self, y, pred, minlen=20):
        """返回该行上满足 pred 的最长连续 x 区间。"""
        best = None
        cur = None
        for x in range(self.w):
            if pred(self.px(x, y)):
                if cur is None:
                    cur = x
            else:
                if cur is not None:
                    if best is None or (x - cur) > (best[1] - best[0]):
                        best = (cur, x - 1)
                    cur = None
        if cur is not None and (best is None or (self.w - cur) > (best[1] - best[0])):
            best = (cur, self.w - 1)
        if best and best[1] - best[0] >= minlen:
            return best
        return None

    def col_span(self, x, y0, y1, pred, minlen=10):
        best = None
        cur = None
        for y in range(y0, y1):
            if pred(self.px(x, y)):
                if cur is None:
                    cur = y
            else:
                if cur is not None:
                    if best is None or (y - cur) > (best[1] - best[0]):
                        best = (cur, y - 1)
                    cur = None
        if cur is not None and (best is None or (y1 - cur) > (best[1] - best[0])):
            best = (cur, y1 - 1)
        if best and best[1] - best[0] >= minlen:
            return best
        return None


def near(c, t, tol=12):
    return all(abs(c[i] - t[i]) <= tol for i in range(3))


def hexs(c):
    return '#%02X%02X%02X' % c
