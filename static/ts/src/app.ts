import express from 'express';
import path from 'path';

const app = express();

// app.get('/', (req, res) => {
//   res.send('Hello, TypeScript!');
// });

app.use(express.static(path.join(__dirname, 'public')));

app.get('/', (req, res) => {
  res.sendFile(path.join(__dirname, 'public', 'index.html'));
});


const port = 3000;
app.listen(port, () => {
  console.log(`Server is running on http://localhost:${port}`);
});
